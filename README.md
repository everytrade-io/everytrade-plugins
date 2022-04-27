# everytrade-plugins

## Enhance an existing module with new components
1. Fork this repository.
1. Implement your new plugin in `plugin-base` OR just use an already existing plugin and add just your new extensions
   (e.g. a new connector, parser, etc.). In any case, you can use the template classes located in `plugin-template`
   module, copy the appropriate ones to your desired location and start customizing them.
   If you don't want to add a new plugin but use an existing plugin as parent instead, don't forget to change the parent
   plugin's code to include/reference your new functionality where appropriate.
## Customize the template code
- your Gradle module can contain multiple plugins and each plugin can contain multiple connectors, parsers, or other
  extensions. Add plugins/extensions as appropriate (see other parts of this document for more details).
- Rename the template classes and packages as you see appropriate.
- Choose a unique plugin ID (a good approach is to base the name off of you organization's name)

### Adding a new connector
- Choose a connector ID unique in the scope of your plugin
- Choose your connector's parameters and create a connector descriptor. This will be transformed into a user-facing
  form to fill the connector's instance parameters (usually credentials, remote URL, etc.).
- Implement `IConnector` and its methods (most importantly the `getTransactions` method).
- You can take a look at the `plugin-base` module for some inspiration. It contains some real plugin implementations.
- Respect connector limitations:
    - There's a limit on how many transactions can be downloaded during a single download pass (10,000). Don't try
      to download more than the single-pass limit. If your transaction source has additional data, you will dowload
      more during the next download pass. Connectors are called periodically (multiple times per hour) to keep the
      portfolios in sync with the source of data, so this shouldn't be a problem. Eventually, your portfolio will
      become synchronized.
    - Close all unneeded resources (if present) in the `close` method which is called when the connector instance
      goes out of scope. In order to save resources, connector instances are destroyed after each download pass.

### Adding a new parser
- Choose a parser ID unique in the scope of your plugin.
- Create a parser descriptor. It describes which files the parser is able to parse - using file header templates and
  supported exchange for each header template. The header template can directly match the header in the file or can
  be entered like a regular expression.
- Implement `ICsvParser` and its methods (most importantly the `parse` method - which should parse a csv file with
  some specific header template).
- You can take a look at the `plugin-base` module for some inspiration. It contains some real plugin implementations.

### Adding a new fiat currency
Edit the `Currency` enum and add the appropriate currency. Set the enum constructor parameters accordingly. After
the code makes it's way into the next Everytrade release, our fiat rates service should recognize your currency and
start downloading the rates at startup.

### Adding a new crypto currency
Edit `Currency` enum and add the appropriate currency. Set the enum constructor parameters accordingly. Make sure
there is a rate provide (`IRateProvider` implementation) for the currency (supporting both USD and BTC as quotes). Since
you're adding a new currency, you'll probably have to extend an existing crypto

rate provider or write a new one for
your currency.

### Adding a crypto rate provider
- Choose a rate provider ID unique in the scope of your plugin.
- Create a rate provider descriptor. It lists the currencies whose rates can be supplied by this rate provider.
- Implement `IRateProvider` and its methods. Make sure your provider supports USD as well as BTC rates for your
  currency (e.g. when adding LTC rate provider, you should support rates for LTC/USD and LTC/BTC).
- Again, `plugin-base` module contains some real implementations you can use as inspiration.

### Adding a new exchange
Just add a new entry to the `SupportedExchange` enum.

## Implementing new components as a new module
1. Fork this repository.
1. Add new Gradle module:
    1. Duplicate the directory `plugin-template` which server as a plugin implementation template.
    1. Include the new module in `settings.gradle`.
    1. Customize your plugin's `build.gradle` (module's `group` and jar manifest). Don't touch the rest if you don't
       know what you're doing. Specifically, the initial set od Gradle dependencies is the recommended minimum. You can
       add libraries as you progress with the plugin implementation, but you shouldn't remove any of the initial
       dependencies.
1. Customize the template code (as described before).

## Using libraries
To declare your plugin dependencies via Gradle, use only `compileOnly`, `pluginCompile` or `pluginRuntime` dependency
configurations depending on whether you need the library during compilation only, compilation and runtime or only
runtime, respectively.

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
1. Prepare test CSV files with transactions which you want to parse in the `parser-files` directory.
1. Run the Gradle task `:plugin-tester:run` either via your IDE of choice (recommended, as you can debug your plugin
   this way) or via the command line (in the repository's root dir, run `./gradlew :plugin-tester:run`).
1. Watch the tester's output to determine whether correct data is downloaded and correct data is parsed.
    - For the tested connector the tester specifically tries two passes of the download procedure in order to check
     whether your connector can correctly resume download from where it left off after the first download pass. In
      order to fully test this, you should adjust your connector when testing to limit the amound of data being
      downloaded in a single pass.
   - The tester tries to parse each file in the `parser-files` directory with an appropriate parser. A parser is chosen
     based on a match between the CSV file's header and one of the parser's advertised headers.

## Contribute back and make it part of Everytrade cloud instance
How to create a new plugin and make it part of the Base Plugin Pack:
1. Fork this repository on GitHub.
1. Implement your plugin inside the `plugin-base` module.
1. Test your plugin with the built-in tester.
1. Create pull request.
1. Make it through our code review.

## Deploying to on-premise instance
1. Build you plugin module's JAR
1. Determine you docker volume mountpoint:
   ```
    $ docker volume inspect everytrade_webapp-data | grep Mountpoint
   ```
   Most probably the output should match the following:
   ```
              "Mountpoint": "/var/lib/docker/volumes/everytrade_webapp-data/_data",
   ```
1. Copy the JAR into the `plugins` subdirectory located underneath the mountpoint (i.e. for the mountpoint
`/var/lib/docker/volumes/everytrade_webapp-data/_data` copy the JAR into
`/var/lib/docker/volumes/everytrade_webapp-data/_data/plugins` directory)
1. Restart the Everytrade webapp container:
   ```
   $ docker container restart everytrade_webapp_1
   ```
1. Check the container logs whether your plugin has been loaded:
   ```
   $ docker logs everytrade_webapp_1
   ```
   and look for something like the following in the output:
   ```
   [...]
   2020-09-11 14:44:39,660 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Reloading plugins...
   2020-09-11 14:44:39,734 INFO  [pf4j.AbstractPluginManager] (ServerService Thread Pool -- 88) Plugin 'everytrade-base@1.0.3' resolved
   2020-09-11 14:44:39,735 INFO  [pf4j.AbstractPluginManager] (ServerService Thread Pool -- 88) Start plugin 'everytrade-base@1.0.3'
   2020-09-11 14:44:39,810 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Loading plugin 'generalbytes'...
   2020-09-11 14:44:39,814 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Found connector 'generalbytes.GBConnector'.
   2020-09-11 14:44:39,818 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Finished loading plugin 'generalbytes'.
   2020-09-11 14:44:39,820 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Loading plugin 'everytrade'...
   2020-09-11 14:44:39,820 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Found connector 'everytrade.krkApiConnector'.
   2020-09-11 14:44:39,820 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Found connector 'everytrade.bitstampApiConnector'.
   2020-09-11 14:44:39,820 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Found connector 'everytrade.etApiConnector'.
   2020-09-11 14:44:39,821 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Found connector 'everytrade.coinmateApiConnector'.
   2020-09-11 14:44:39,821 INFO  [ejb.PluginRegistry] (ServerService Thread Pool -- 88) Finished loading plugin 'everytrade'.
   [...]
   ```
   You should see your components being loaded.
   ## Implemented exchange API connectors and parsers
   |Plugin ID| Connector/parser ID|Type|Exchange|Notes|
   |--|-------|--------|--------|--------|
   |generalbytes|generalbytes.GBConnector|Connector|General Bytes||
   |everytrade|everytrade.everytradeParser|Parser|General Bytes||
   |everytrade|everytrade.etApiConnector|Connector|EveryTrade||
   |everytrade|everytrade.everytradeParser|Parser|EveryTrade||
   |everytrade|everytrade.krkApiConnector|Connector|Kraken||
   |everytrade|everytrade.everytradeParser|Parser|Kraken||
   |everytrade|everytrade.bitstampApiConnector|Connector|Bitstamp||
   |everytrade|everytrade.everytradeParser|Parser|Bitstamp||
   |everytrade|everytrade.coinmateApiConnector|Connector|CoinMate||
   |everytrade|everytrade.everytradeParser|Parser|CoinMate||
   |everytrade|everytrade.bitfinexApiConnector|Connector|Bitfinex||
   |everytrade|everytrade.everytradeParser|Parser|Bitfinex||
   |everytrade|everytrade.binanceApiConnector|Connector|Binance||
   |everytrade|everytrade.everytradeParser|Parser|Binance||
   |everytrade|everytrade.bittrexApiConnector|Connector|Bittrex||
   |everytrade|everytrade.everytradeParser|Parser|Bittrex||
   |everytrade|everytrade.coinbaseProApiConnector|Connector|Coinbase Pro||
   |everytrade|everytrade.everytradeParser|Parser|Coinbase Pro||
   |everytrade|everytrade.bitmexApiConnector|Connector|Bitmex||
   |everytrade|everytrade.everytradeParser|Parser|Bitmex||
   |everytrade|everytrade.huobiApiConnector|Connector|Huobi||
   |everytrade|everytrade.everytradeParser|Parser|Huobi||
   |everytrade|everytrade.okxApiConnector|Connector|OKX||
   |everytrade|everytrade.everytradeParser|Parser|OKX||
   |everytrade|everytrade.everytradeParser|Parser|bitFlyer||
   |everytrade|everytrade.everytradeParser|Parser|Coinsquare||
   |everytrade|everytrade.everytradeParser|Parser|HitBTC||
   |everytrade|everytrade.everytradeParser|Parser|LocalBitcoins||
   |everytrade|everytrade.everytradeParser|Parser|Paxful||
   |everytrade|everytrade.everytradeParser|Parser|Poloniex||
   |everytrade|everytrade.everytradeParser|Parser|ShakePay||
