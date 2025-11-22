#!/bin/sh
set -x

echo "Evaluating Not Sure Options"

reRuns=0;

total=10;
current=0;

p='elexplicator'
u='service'

more=0;

examples_dir='/home/service/Desktop/Examples'
intermediate_out_dir='/home/service/Desktop/Examples'

echo "Watching out for -> process = "$p", user = "$u
if ! id -u $u > /dev/null;
then
	more=2;
fi

#CLASS_PATH="target/classes"
JAR_PATH="target/ELExplicator.jar"

CLASS_NAME="de.tu_dresden.lat.evaluate.RunEvaluation"


java -cp "$JAR_PATH" "$CLASS_NAME" "$examples_dir" "$intermediate_out_dir" -XX:+ExitOnOutOfMemoryError
            
