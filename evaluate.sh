#!/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'
set -x

echo "Evaluating Not Sure Options"

reRuns=0;

total=10;
current=0;

p='elexplicator'
u='service'

more=0

checkpoint='/home/service/elexplicator/OptionsEval/programState.json'


examples_dir='/home/service/Desktop/Examples'
intermediate_out_dir='/home/service/Desktop/Examples'

echo "Watching out for -> process = $p, user = $u"
if ! id -u "$u" &> /dev/null;
then
	more=2;
fi

#CLASS_PATH="target/classes"
JAR_PATH="target/ELExplicator.jar"

CLASS_NAME="de.tu_dresden.lat.evaluate.RunEvaluation2"

status_value="Failure";
normalized='True';

while (( more == 0 ));
do 
	if (( current == 0 ));
	then 
		current=$total
		if ! pgrep -u "$u" -x "$p" > /dev/null;
		then 
			if (( reRuns > 0 ));
			then 
				status_value=$(awk -F'"' '/"status"/ {print $4; exit}' "$checkpoint");
				if [[ "$status_value" == "Success" ]];
				then 
					echo "Process completed successfully. Exiting.";
					more=1;
					exit 1;
				fi
			fi
			set +e
			if (( reRuns == 0 ));
			then 
				java -Xmx8g -Xms2g -cp "$JAR_PATH" "$CLASS_NAME" "$examples_dir" "$intermediate_out_dir" "False" "$normalized" -XX:+ExitOnOutOfMemoryError 
			else
				java -Xmx8g -Xms2g -cp "$JAR_PATH" "$CLASS_NAME" "$examples_dir" "$intermediate_out_dir" "True" "$normalized" -XX:+ExitOnOutOfMemoryError 
			fi
			set -e
			reRuns=$((reRuns + 1));
			sleep 5;
		fi
	else
		current=$((current - 1));
		sleep 5;
	fi
done



            
