"""
stid_download.py - Download STID documents into a task folder.

Downloads engineering documents (P&IDs, datasheets, SCDs, GA drawings) from
STID into the task's step1_scope_and_research/references/ directory.
Optionally converts PDFs to PNGs in the task's figures/ directory.

Usage:
    # Download by tag list
    python devtools/stid_download.py --task-dir task_solve/2026-04-16_my_task \
        --inst MYINST --tags 30PT0001 30PT0002 33AI0001

    # Download specific documents by doc number
    python devtools/stid_download.py --task-dir task_solve/2026-04-16_my_task \
        --inst MYINST --docs E001-AS-P-XB-00001-01 E001-AS-BI000-DS-00001

    # Download and convert PDFs to PNG for AI analysis
    python devtools/stid_download.py --task-dir task_solve/2026-04-16_my_task \
        --inst MYINST --tags 30PT0001 --convert-png

Requirements:
    pip install stidapi
    pip install pymupdf   (only for --convert-png)
"""
import argparse
import json
import os
import sys


def get_docs_for_tags(inst_code, tag_list):
    """Retrieve unique documents referenced by STID tags.

    Args:
        inst_code: STID installation code (e.g., 'MYINST')
        tag_list: List of tag numbers to search

    Returns:
        Dict mapping doc_no to document metadata
    """
    from stidapi import Tag

    all_docs = {}
    for tag_no in tag_list:
        tags = Tag.search(inst_code=inst_code, tag_no=tag_no)
        if not tags:
            print("  [WARN] Tag not found: {}".format(tag_no))
            continue
        t = tags[0]
        docs = t.get_doc_references()
        for d in docs:
            doc_no = d.get("docNo", "")
            if doc_no and doc_no not in all_docs:
                all_docs[doc_no] = {
                    "docNo": doc_no,
                    "docTitle": d.get("docTitle", ""),
                    "docType": d.get("docType", ""),
                    "files": d.get("files", []),
                    "tags_referencing": [tag_no],
                }
            elif doc_no in all_docs:
                all_docs[doc_no]["tags_referencing"].append(tag_no)
    return all_docs


def download_doc_files(inst_code, all_docs, out_dir):
    """Download PDF files for documents into out_dir.

    Args:
        inst_code: STID installation code
        all_docs: Dict of document metadata (from get_docs_for_tags)
        out_dir: Absolute path to output directory

    Returns:
        Tuple of (downloaded_list, failed_list)
    """
    import stidapi.utils as u

    client = u.get_api_client()
    api_url = u.get_api_url()
    os.makedirs(out_dir, exist_ok=True)

    downloaded = []
    failed = []

    for doc_no, info in sorted(all_docs.items()):
        for f in info.get("files", []):
            fname = f.get("fileName") or ""
            file_id = f.get("id", None)
            blob_id = f.get("blobId") or ""

            if not fname.lower().endswith(".pdf") or not file_id:
                continue

            safe_name = doc_no.replace("/", "_") + ".pdf"
            out_path = os.path.join(out_dir, safe_name)

            if os.path.exists(out_path) and os.path.getsize(out_path) > 1000:
                print("  [SKIP] Already exists: {}".format(safe_name))
                downloaded.append(
                    {"docNo": doc_no, "title": info["docTitle"],
                     "file": safe_name, "status": "cached"}
                )
                continue

            urls_to_try = [
                "{}{}//file/{}".format(api_url, inst_code, file_id),
                "{}{}/file/blob/{}".format(api_url, inst_code, blob_id),
            ]

            success = False
            for url in urls_to_try:
                try:
                    client.get_file(url=url, file_name=out_path)
                    if os.path.exists(out_path) and os.path.getsize(out_path) > 1000:
                        print("  [OK] {}: {} ({} bytes)".format(
                            doc_no, info["docTitle"], os.path.getsize(out_path)))
                        downloaded.append(
                            {"docNo": doc_no, "title": info["docTitle"],
                             "file": safe_name, "status": "downloaded"}
                        )
                        success = True
                        break
                    else:
                        if os.path.exists(out_path):
                            os.remove(out_path)
                except Exception:
                    if os.path.exists(out_path):
                        os.remove(out_path)
                    continue

            if not success:
                print("  [FAIL] {}: {}".format(doc_no, info["docTitle"]))
                failed.append(
                    {"docNo": doc_no, "title": info["docTitle"],
                     "error": "all URL patterns failed"}
                )

    return downloaded, failed


def convert_pdfs_to_png(references_dir, figures_dir, dpi=200, prefix="stid_"):
    """Convert downloaded PDFs to PNG images for AI analysis.

    Args:
        references_dir: Directory containing PDF files
        figures_dir: Directory to write PNG files
        dpi: Resolution for PNG conversion
        prefix: Prefix for output PNG filenames
    """
    try:
        import fitz  # pymupdf
    except ImportError:
        print("[WARN] pymupdf not installed, skipping PNG conversion")
        print("       Install with: pip install pymupdf")
        return []

    os.makedirs(figures_dir, exist_ok=True)
    converted = []

    for fname in sorted(os.listdir(references_dir)):
        if not fname.lower().endswith(".pdf"):
            continue
        pdf_path = os.path.join(references_dir, fname)
        base = os.path.splitext(fname)[0]
        png_name = "{}{}.png".format(prefix, base)
        png_path = os.path.join(figures_dir, png_name)

        if os.path.exists(png_path):
            print("  [SKIP] PNG exists: {}".format(png_name))
            converted.append(png_path)
            continue

        try:
            doc = fitz.open(pdf_path)
            # Use first page as representative image
            page = doc[0]
            pix = page.get_pixmap(dpi=dpi)
            pix.save(png_path)
            doc.close()
            print("  [OK] {} -> {}".format(fname, png_name))
            converted.append(png_path)
        except Exception as e:
            print("  [FAIL] {}: {}".format(fname, str(e)[:60]))

    return converted


def save_manifest(out_dir, downloaded, failed, inst_code, tags=None, doc_nos=None):
    """Save a retrieval manifest for traceability.

    Args:
        out_dir: Directory to write manifest
        downloaded: List of downloaded document dicts
        failed: List of failed document dicts
        inst_code: STID installation code
        tags: Tags searched (optional)
        doc_nos: Document numbers requested (optional)
    """
    manifest = {
        "source": "stidapi",
        "inst_code": inst_code,
        "tags_searched": tags or [],
        "doc_nos_requested": doc_nos or [],
        "documents_retrieved": downloaded,
        "documents_failed": failed,
    }
    manifest_path = os.path.join(out_dir, "stid_retrieval_manifest.json")
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
    print("\nManifest saved: {}".format(manifest_path))


def main():
    parser = argparse.ArgumentParser(
        description="Download STID documents into a task folder"
    )
    parser.add_argument(
        "--task-dir", required=True,
        help="Path to the task folder (e.g., task_solve/2026-04-16_my_task)"
    )
    parser.add_argument(
        "--inst", required=True,
        help="STID installation code (e.g., MYINST)"
    )
    parser.add_argument(
        "--tags", nargs="+", default=[],
        help="Tag numbers to search for documents"
    )
    parser.add_argument(
        "--docs", nargs="+", default=[],
        help="Specific document numbers to download"
    )
    parser.add_argument(
        "--convert-png", action="store_true",
        help="Convert downloaded PDFs to PNG for AI analysis"
    )
    parser.add_argument(
        "--dpi", type=int, default=200,
        help="DPI for PDF-to-PNG conversion (default: 200)"
    )
    args = parser.parse_args()

    # Resolve output directories inside the task folder
    task_dir = os.path.abspath(args.task_dir)
    references_dir = os.path.join(task_dir, "step1_scope_and_research", "references")
    figures_dir = os.path.join(task_dir, "figures")

    if not os.path.isdir(task_dir):
        print("ERROR: Task directory does not exist: {}".format(task_dir))
        print("Create it first: neqsim new-task \"your task\"")
        sys.exit(1)

    os.makedirs(references_dir, exist_ok=True)

    print("=== STID Document Download ===")
    print("Task folder: {}".format(task_dir))
    print("Output dir:  {}".format(references_dir))
    print()

    all_docs = {}

    # Discover documents by tag
    if args.tags:
        print("Searching tags: {}".format(", ".join(args.tags)))
        all_docs = get_docs_for_tags(args.inst, args.tags)
        print("Found {} unique documents\n".format(len(all_docs)))

    # Add specific document numbers
    if args.docs:
        for doc_no in args.docs:
            if doc_no not in all_docs:
                all_docs[doc_no] = {
                    "docNo": doc_no,
                    "docTitle": "(requested directly)",
                    "docType": "",
                    "files": [{"id": None, "fileName": doc_no + ".pdf", "blobId": ""}],
                    "tags_referencing": [],
                }

    if not all_docs:
        print("No documents to download. Provide --tags or --docs.")
        sys.exit(1)

    # Download
    print("Downloading {} documents...".format(len(all_docs)))
    downloaded, failed = download_doc_files(args.inst, all_docs, references_dir)
    print("\n=== Summary: {} downloaded, {} failed ===".format(
        len(downloaded), len(failed)))

    # Save manifest
    save_manifest(references_dir, downloaded, failed, args.inst,
                  tags=args.tags, doc_nos=args.docs)

    # Convert to PNG
    if args.convert_png:
        print("\nConverting PDFs to PNG (dpi={})...".format(args.dpi))
        converted = convert_pdfs_to_png(references_dir, figures_dir,
                                        dpi=args.dpi, prefix="stid_")
        print("Converted {} files".format(len(converted)))


if __name__ == "__main__":
    main()
