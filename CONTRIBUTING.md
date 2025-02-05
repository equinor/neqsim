# Contributing

When contributing to this repository, please first discuss the change you wish to make via issue,
email, or any other method with the owners of this repository before making a change. 

Please note we have a code of conduct, please follow it in all your interactions with the project.

## Pull Request Process

1. Ensure any install or build dependencies are removed before the end of the layer when doing a 
   build.
2. Update the README.md with details of changes to the interface, this includes new environment 
   variables, exposed ports, useful file locations and container parameters.
3. Increase the version numbers in any examples files and the README.md to the new version that this
   Pull Request would represent. The versioning scheme we use is [SemVer](http://semver.org/).
4. You may merge the Pull Request in once you have the sign-off of two other developers, or if you 
   do not have permission to do that, you may request the second reviewer to merge it for you.

## Style guideline

This project uses Google Java style formatting rules. 

### Visual Studio Code:  

Install extensions [Language Support for Java(TM) by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java) and [Checkstyle for Java
](https://marketplace.visualstudio.com/items?itemName=shengchen.vscode-checkstyle) and add the following to settings.json.
 ```   
  "java.configuration.updateBuildConfiguration": "interactive",
  "[java]": {
    "editor.defaultFormatter": "redhat.java",
    "editor.formatOnSave": true,
    "editor.tabSize": 2,
    "editor.insertSpaces": true,
    "editor.detectIndentation": false
  },
  "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml",
  "java.saveActions.organizeImports": true,
  "java.checkstyle.version": "10.18.1",
  "java.checkstyle.configuration": "${workspaceFolder}/checkstyle_neqsim.xml",
  "java.debug.settings.hotCodeReplace": "never",
  "[xml]": {
    "editor.tabSize": 4,
    "editor.insertSpaces": false
  },
  "[json]": {
    "editor.tabSize": 4,
    "editor.insertSpaces": true
  }
```

Note: workspace/project specific settings are located in folder .vscode.

### Eclipse:

Follow the instructions in http://www.practicesofmastery.com/post/eclipse-google-java-style-guide/
