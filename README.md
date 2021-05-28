Install Byteman https://byteman.jboss.org/downloads.html

export MAVEN_OPTS=-javaagent:$BYTEMAN_HOME/lib/byteman.jar=script:amqclient.btm
mvn clean compile exec:java -Dexec.arguments="tcp://xxxxx"





