# fork  fork of java Call-graph 

this is a fork of a fork of the original project java-callgraph

## how built 

use java 11 (not working with java 17)

``` shell
gradle jar 
```

result is located in build/libs/java-callgraph-juliendevelopment-0.2.0.jar

## original projects

orignal project is : https://github.com/gousiosg/java-callgraph/

fork number one : https://github.com/Adrninistrator/java-callgraph

my fork is to add those change 

* https://github.com/gousiosg/java-callgraph/issues/30
* fix the generation of the jar with dependencies and manifest
* better detection of method reference in a stream 