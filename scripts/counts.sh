# Writes all counts in de.saar.coli.amtools.decomposition.analysis Count scripts for all 4 corpus types. Assumes amconll files are called AMR.amconll etc. and are located in $path_prefix/$corpora/.  

jar_path="/home/mego/am-tools/build/libs/am-tools.jar"
path_prefix="/home/mego/Documents/amconll_files/"
corpora="train-final"


for corpus in DM PSD PAS AMR3 AMR4
do
    for count in Sources Edges Supertags
    do
	java -Xmx2G -cp $jar_path de.saar.coli.amtools.decomposition.analysis.Count$count $path_prefix $corpora $corpus
    done
done

