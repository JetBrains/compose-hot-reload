This module represents the actual JVM implementation of the hot reload runtime.

## Development
We do have multiple source directories within this project:

## main
The src/main/kotlin will contain all 'static' parts of the JVM runtime. 
Static means here, that it uses our default 'o.j.compose.reload' package, which gets ignored by 'hot reload' itself

## kotlinUI
The src/main/kotlinUI source directory contains all source code which is UI code
This source directory deliberately uses a different package 'hotReloadUI' to not fall into the 
ignores of hot reload, which makes it possible to build the hot reload UI, using hot reload!

## dev
The src/dev/kotlin source directory contains development entry points which is supposed
to make it easy to launch and iterate on certain components.
