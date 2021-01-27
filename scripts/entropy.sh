jar_path="/home/mego/am-tools/build/libs/am-tools.jar"
path_prefix="/home/mego/Documents/amconll_files/"


for corpus in DM PSD PAS AMR
do
    java -Xmx2G -cp $jar_path de.saar.coli.amtools.decomposition.SupertagEntropy $path_prefix $corpus
done

