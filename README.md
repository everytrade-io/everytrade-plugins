# everytrade-plugin-api

## Enhance an existing module with new components
1. Fork this repository.
1. Implement your plugin in `plugin-base` OR just use an already existing plugin and add just the new functionality
   (e.g. a new connector). In any case, you can use the template classes in `plugin-template` module. If you don't want
   to add a new plugin but use an existing plugin as parent instead, don't forget to change the parent plugin's code
   to include/reference your new functionality where appropriate.
1. Customize the skeleton code:
    - The Gradle module can contain multiple plugins and each plugin can contain multiple connectors. Add 
      plugins/connectors as appropriate.
    - Rename the skeleton classes and packages as you see appropriate.
    - Choose a unique plugin ID (a good approach is to base the name off of you organization's name)
    - Choose a connector ID unique in the scope of your plugin
    - Choose your connector's parameters and create a connector descriptor. This will be transformed into a user-facing
      form to fill the connector's instance parameters (usually credentials, remote URL, etc.).
    - Implement the connector's methods (most importantly the `getTransactions` method).
    - You can take a look at the `plugin-base` module for some inspiration. It contains some real plugin
      implementations.
    - Respect connector limitations:
       - There's a limit on how many transactions can be downloaded during a single download pass (10,000). Don't try
         to download more than the single-pass limit. If your transaction source has additional data, you will dowload
         more during the next download pass. Connectors are called periodically (multiple times per hour) to keep the
         portfolios in sync with the source of data, so this shouldn't be a problem. Eventually, your portfolio will
         become synchronized.
       - Close all unneeded resources (if present) in the `close` method which is called when the connector instance
         goes out of scope. In order to save resources, connector instances are destroyed after each download pass.

## Implementing new components as a new module
1. Fork this repository.
1. Add new Gradle module:
    1. Duplicate the directory `plugin-template` which server as a plugin implementation skeleton.
    1. Include the new module in `settings.gradle`.
    1. Customize your plugin's `build.gradle` (module's `group` and jar manifest). Don't touch the rest if you don't
       know what you're doing. Specifically, the initial set od Gradle dependencies is the recommended minimum. You can
       add libraries as you progress with the plugin implementation, but you shouldn't remove any of the initial
       dependencies.
1. Customize the skeleton code (as described before).
             

## Building custom plugin
1. Run `./gradlew :<your-plugin>:build` (with *<your-plugin>* substituted with the actual Gradle module name you chose)
   in the repository root dir.

## Testing custom plugin
1. The repository contains the `plugin-tester` module to help you with testing your plugin. The tester is already set-up
   to test the `plugin-base` module's plugins.
1. Edit the `plugin-tester/build.gradle` file's task `gatherPlugins` and add a dependency on your plugin module's JAR.
   You can also remove the dependency on `plugin-base` module's JAR if you want to concentrate just on testing your
   own module.
1. Prepare connector credentials and/or other parameters.
    - In order to pass parameters to your connector's instances, you have to create a property file.
    - In the `plugin-tester` directory create a Java property file named `<connector-id>.properties`, 
      e.g. for the Everytrade Kraken Connector, the file name is *everytrade.krkApiConnector.properties* bacause the
      connector's ID is *krkApiConnector* and the connector parent plugin's ID is *everytrade*.
   - The file's content will be loaded into a map as key-value pairs and used to initialize the connector's 
     instance. Your code can read the data in the `createConnectorInstance` method implementation. The template
     implementation just passes the data into the connector's constructor.
1. Run the Gradle task `:plugin-tester:run` either via your IDE of choice (recommended, as you can debug your plugin
   this way) or via the command line (in the repository's root dir, run `./gradlew :plugin-tester:run`).
1. Watch the tester's output to determine whether correct data is downloaded.
1. The tester specifically tries two passes of the download procedure in order to check whether your connector can
   correctly resume download from where it left off after the first download pass. In order to fully test this, you
   should adjust your connector when testing to limit the amound of data being downloaded in a single pass.     

## Contribute back and make it part of Everytrade cloud instance
How to create a new plugin and make it part of the Base Plugin Pack:
1. Fork this repository on GitHub.
1. Implement your plugin inside the `plugin-base` module.
1. Test your plugin with the built-in tester.
1. Create pull request.
1. Make it through our code review.

## Deploying to on-premise instance
1. Build you plugin module's JAR
1. Copy the JAR into your `~/everytrade/plugins/` directory.
1. Restarte the Everytrade webapp container. 