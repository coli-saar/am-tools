# README 

## What is `visualize.py` for?
The `de.saar.coli.amrtagging.formalisms.*.tools.CreateCorpus` or `de.saar.coli.amrtagging.mrp.tools.CreateCorpus`  
(see also [https://github.com/coli-saar/am-parser/wiki/Converting-individual-formalisms-to-AM-CoNLL](am-parser/wiki/Converting-individual-formalisms-to-AM-CoNLL.md))
output contains error messages if a graph was not decomposable.
```bash
java -Xmx300g -cp am-tools/build/libs/am-tools-all.jar de.saar.coli.amrtagging.formalisms.eds.tools.CreateCorpus -c /proj/irtg/amrtagging/SDP/EDS/raw_data/real_train.amr.txt -o ./eds_output --debug > ./eds_output/LOG_DATEI_eds2.log 2> ./eds_output/errfile_eds2.txt
```
*Note* in the future paths may change to `de.saar.coli.amrtagging.mrp.tools.CreateCorpus`.  
This output to stderr contains the graphs in dot format that were not 
decomposable. The `visualize.py` script extracts those dot formatted graphs and 
actually uses dot to convert them into more readable pdfs (or pngs if you like).
Moreover, this script logs the concrete sentences belonging to the 
non-decomposable graphs as well as the number of tokens 
(useful to search for minimal not-working examples).  
*Version 2*: Added extraction and print out of the predicted constants of 
non-decomposable graphs.  
*Version 3*: Adapted to new input format which also provides EDS id. This id is
also extracted.  
*Version 4*: added command line options `maxsentsize`, `minsentsize`, 
`showconsts`, code cleanup  


## How to run
- **only tested with EDS/DM so far** (see `CreateCorpus.java` of EDS for how 
error file, i.e. input of this script, needs to be formatted)
- This script was developed using Python 3.7
- `graphviz` needs to be installed, since the `dot` executable is called 
from the python script. 
[http://graphviz.org/download/](http://graphviz.org/download/)
- in order to extract the constants in penman format we need the Penman package
  `pip install Penman`  
  [https://pypi.org/project/Penman/](https://pypi.org/project/Penman/) or 
  [https://github.com/goodmami/penman](https://github.com/goodmami/penman)
  (I've tested it with  Penman-0.6.2 )

General usage:  
```
usage: visualize.py [-h] [--outformat {pdf,png,jpg,eps,svg,bmp}]
                    [--showconsts] [--minsentsize MINSENTSIZE]
                    [--maxsentsize MAXSENTSIZE]
                    inputfile outputdir

extract non-decomposable graphs and visualize them

positional arguments:
  inputfile             Path to input file (errors of CreateCorpus)
  outputdir             Path to output directory

optional arguments:
  -h, --help            show this help message and exit
  --outformat {pdf,png,jpg,eps,svg,bmp}
                        Outputformat of dot (default: pdf)
  --showconsts          Ignore constants (default: True)
  --minsentsize MINSENTSIZE
                        Minimum sentence size (default: 0)
  --maxsentsize MAXSENTSIZE
                        Maximum sentence size (default: -1) int < 0 converted
                        to +infinity
```

If you supply just the two required input arguments (inputfile and outputdir),
the script will extract all non-decomposable graphs but not the predicted 
constants. The non-decomposable graphs are printed to PDF.  
If you would like to print the graphs to PNG files instead of PDF, add 
`--outformat png`.  
If you would like to also extract constants, add `--showconsts`.  
If you would like to extract only those sentences with minimum sentence length
 of 10, add `--minsentsize 10`.  
If you would like to extract only those sentences with maximum sentence length
 of 20, add `--maxsentsize 20`.  
Of course you can also combine the aforementioned options.


### Example usages
(requires specific directory structure)  
`python3 visualize.py ../visualizer_data/input/eds/errfile_eds.txt ../visualizer_data/output/eds --outformat pdf`  
`python3 visualize.py ../visualizer_data/input/eds/errfile_eds.txt ../visualizer_data/output/eds --outformat png`  
`python3 visualize.py ../visualizer_data/input/eds/errfile_eds.txt ../visualizer_data/output/eds --showconsts --maxsentsize 15`  


### Input
File with all error messages (everything that was redirected from stderr to 
a file) from the `CreateCorpus` call.
In the java code something like ...
```java
} else {
        problems ++;
        System.err.println("id "+ids.get(counter));
        System.err.println(inst.getGraph());
        System.err.println("not decomposable " + inst.getSentence());
        if (cli.debug){
                for (Alignment al : inst.getAlignments()){
                        System.err.println(inst.getSentence().get(al.span.start));
                        System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
                }
                System.err.println(GraphvizUtils.simpleAlignViz(inst, true));
        }
        System.err.println("=====end not decomposable=====");
}
```
in errfile:
```text
id NUMBER
SOMETHING
not decomposable [tok1, ..., tokn]
word1
graph constants of word1
word2
graph constants of word2
...MORECONSTANTS...
wordn
graph constants of wordn
digraph {
  ...SOMETHING...
}
=====end not decomposable=====
```

### Output
- a `sentlength.log` file in tab separated format:  
  for each non-decomposable graph prints one line:  
  `INFO<TAB>not decomposable<TAB>ID<TAB>SENTNO<TAB>SENTLENGTH<TAB>LINENUMBER<TAB>SENTENCE`  
  e.g. `INFO  not decomposable  20010002  004  4  826  Not this year .`  
  (4th error-causing graph was non decomposable (004), eds id is 20010002, four 
  tokens (4), found at line number 659 of the input file, then all tokens 
  separated by whitespace)
- under the specified `OUTPUTDIR` two folders are created: 
    - a `dot` folder  containing the graphs extracted form the input file and
    - a `pdf` (or `png` or whatever format corresponding to the 
    specified `outformat`) folder containing the output of graphviz dot
    - Note: I've added a caption (namely the whitespace separated tokens of the 
    sentence) to the dot output for the full graph.
    - for each non decomposable graph the corresponding files start with the 
    respective `SENTNO` (e.g. `004`)
    - Note for version 2: added extraction and dot printing of constants for 
    non-decomposable graphs 
    (format: `SENTID_const_Tokennumber_Token_Graphnr.pdf`
    e.g. `001_const_13_under_2.pdf` is the second graph constant predicted in 
    sentence 001 for the 13th token present in the error message for this 
    sentence (token is 'under'))
    - **TODO**: extract other error messages, errors like
    ```
    Ignoring an exception:
    java.lang.IllegalArgumentException: Cannot create a constant for this alignment (explicitanon_u_371|e52|explicitanon_u_356|e4!||15-16||1,0): More than one node with edges from outside, but we can only have one root.
        at de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder.getConstantsForAlignment(ConcreteAlignmentSignatureBuilder.java:292)
        at de.saar.coli.amrtagging.ConcreteAlignmentTrackingAutomaton.create(ConcreteAlignmentTrackingAutomaton.java:133)
        at de.saar.coli.amrtagging.ConcreteAlignmentTrackingAutomaton.create(ConcreteAlignmentTrackingAutomaton.java:109)
        at de.saar.coli.amrtagging.formalisms.eds.tools.CreateCorpus.main(CreateCorpus.java:129)
    ```
    or 
    ```
    Ignoring an exception:
    java.lang.UnsupportedOperationException: Graph cannot be represented as AMR: unvisited nodes.
        at de.up.ling.irtg.codec.SgraphAmrOutputCodec.write(SgraphAmrOutputCodec.java:63)
        at de.up.ling.irtg.codec.SgraphAmrOutputCodec.write(SgraphAmrOutputCodec.java:40)
        at de.up.ling.irtg.codec.OutputCodec.asString(OutputCodec.java:97)
        at de.up.ling.irtg.algebra.graph.SGraph.toIsiAmrStringWithSources(SGraph.java:626)
        at de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder.getConstantsForAlignment(ConcreteAlignmentSignatureBuilder.java:366)
        at de.saar.coli.amrtagging.ConcreteAlignmentTrackingAutomaton.create(ConcreteAlignmentTrackingAutomaton.java:133)
        at de.saar.coli.amrtagging.ConcreteAlignmentTrackingAutomaton.create(ConcreteAlignmentTrackingAutomaton.java:109)
        at de.saar.coli.amrtagging.formalisms.eds.tools.CreateCorpus.main(CreateCorpus.java:129)
    ```
    or maybe
    `***WARNING*** more than one edge at node null`


## Author
Pia Weißenhorn
piaw@coli.uni-saarland.de
