#! /bin/bash

# amconll_prediction is the amconll file with your predictions

# either set AM_TOOLS yourself with export, or it will use the following default path
AM_TOOLS=${AM_TOOLS:-build/libs/am-tools.jar}

amconll_prediction=$1
output=$2
gold_dev_sdp=$3

# Preparation
if [ $# -lt 3 ]; then
    echo "evaluate_dm_pas.sh <amconll_prediction> <output> <gold_dev_sdp>"
    exit 3
fi

if [ -n "$JAVA_HOME" ]; then
    JAVA=$JAVA_HOME/bin/java
else
    JAVA=java
fi

# Run evaluation
 $JAVA -cp $AM_TOOLS de.saar.coli.amrtagging.formalisms.sdp.dm.tools.ToSDPCorpus -c "$amconll_prediction" -o "$output" -g "$gold_dev_sdp"
