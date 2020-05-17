#! /bin/bash

# wordnet_path is the path to Wordnet, as downloaded by am-parser/scripts/setup_AMR.sh
# lookup is the directory of AMR lookup files, generated during preprocessing
# output_dir is the directory to which outputs are written; it must contain goldAMR.txt
# amconll_prediction is the amconll file with your predictions

# either set SMATCH yourself with export, or it will use the following default path
SMATCH=${SMATCH:-../am-parser/external_eval_tools/smatch/smatch.py}

# either set AM_TOOLS yourself with export, or it will use the following default path
AM_TOOLS=${AM_TOOLS:-build/libs/am-tools.jar}

wordnet_path=$1
lookup=$2
output_dir=$3
amconll_prediction=$4

# Preparation
if [ $# -lt 4 ]; then
    echo "evaluate_amr.sh <wordnet_path> <lookup> <output_dir> <amconll_prediction>"
    exit 3
fi

if [ ! -f $SMATCH ]; then
    echo "smatch.py not found at path $SMATCH; export SMATCH to the right path."
    exit 1
fi

if [ ! -f "$output_dir/goldAMR.txt" ]; then
    echo "Output directory '$outdir' does not contain goldAMR.txt; please copy it there first."
    exit 2
fi

# Evaluate AM-CoNLL to graphs (= eval_AMR_new.sh)
echo "Evaluate AM-CoNLL to graphs ..."
java -Xmx2G -cp $AM_TOOLS de.saar.coli.amrtagging.formalisms.amr.tools.EvaluateCorpus -c "$amconll_prediction" -o "$output_dir" --relabel --wn "$wordnet_path" --lookup "$lookup" --th 10

# Compute Smatch (= smatch_AMR.sh)
echo ""
echo "Compute Smatch score ..."
sed ':a;N;$!ba;s/\n/\n\n/g' "$output_dir/goldAMR.txt" > "$output_dir/goldAMR_newlines.txt"
python2 $SMATCH -f "$output_dir/parserOut.txt" "$output_dir/goldAMR_newlines.txt" --significant 4 --pr | tee $output_dir/smatch.txt

