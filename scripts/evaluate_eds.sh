#! /bin/bash

# ./scripts/evaluate_eds.sh <output_prefix> <gold_prefix> <prediction.amconll>

# output_prefix is the prefix to which the AMR and EDM files will be written, e.g. to
# "$output_prefix.amr.txt". A good choice would be "<some_path>/predictions".

# gold_prefix is the prefix for the gold files. E.g. choose "EMNLP20/EDS/test/test-gold"
# to use "EMNLP20/EDS/test/test-gold.amr.txt" etc.

# prediction.amconll is the AM-CoNLL file with the parser predictions.


# either set SMATCH yourself with export, or it will use the following default path
SMATCH=${SMATCH:-../am-parser/external_eval_tools/smatch/smatch.py}

# either set EDM yourself with export, or it will use the following default path
EDM=${EDM:-../am-parser/external_eval_tools/edm/eval_edm.py}

# either set AM_TOOLS yourself with export, or it will use the following default path
AM_TOOLS=${AM_TOOLS:-build/libs/am-tools.jar}


output_prefix=$1
gold_prefix=$2
amconll_prediction=$3




# Preparation
if [ $# -lt 2 ]; then
    echo "evaluate_eds.sh <output_prefix> <amconll_prediction>"
    exit 3
fi

if [ ! -f $SMATCH ]; then
    echo "smatch.py not found at path $SMATCH; export SMATCH to the right path."
    exit 1
fi

if [ ! -f $EDM ]; then
    echo "eval_edm.py not found at path $EDM; export EDM to the right path."
    exit 1
fi


# Evaluate AM-CoNLL to graphs
echo "Evaluate AM-CoNLL to graphs ..."
java -Xmx2G -cp $AM_TOOLS de.saar.coli.amrtagging.formalisms.eds.tools.EvaluateCorpus -c "$amconll_prediction" -o "$output_prefix"

# Compute Smatch
echo ""
echo "Compute Smatch score ..."
python2 $SMATCH -f "$output_prefix.amr.txt" "$gold_prefix.amr.txt"  --pr > $output_prefix.smatch.txt
echo "Smatch score is in $output_prefix.smatch.txt"

# file contents then look like this:
# Precision: 0.87
# Recall: 0.90
# F-score: 0.88


echo "Compute EDM score ..."
python2 ../am-parser/external_eval_tools/edm/eval_edm.py "$output_prefix.edm" "$gold_prefix.edm" > $output_prefix.edm.txt
echo "EDM score is in $output_prefix.edm.txt"

# file contents then look like this:
# All
# F1-score: 83.91 
# All, start spans only
# F1-score: 86.27 
# All, predicates only
# F1-score: 88.69 
# All, predicates only, start spans only
# F1-score: 90.53 
# All, predicates only - without spans
# F1-score: 93.04 
# All, relations only
# F1-score: 77.91 
# All, relations only, start spans only
# F1-score: 80.94 
