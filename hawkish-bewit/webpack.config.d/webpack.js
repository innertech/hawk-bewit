const NodePolyfillPlugin = require("node-polyfill-webpack-plugin");

//  https://github.com/square/okio/issues/1163
config.plugins.push(
  new NodePolyfillPlugin()
)
