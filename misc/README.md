# README 

## What is `visualize.py` for?
The `de.saar.coli.amrtagging.formalisms.*.tools.CreateCorpus`  
(see also `am-parser.wiki/Converting-individual-formalisms-to-AM-CoNLL.md`)
output contains error messages if a graph was not decomposable.
```
mkdir eds
java -cp build/libs/am-tools-all.jar de.saar.coli.amrtagging.formalisms.eds.tools.CreateCorpus -c <eds_train.amr.txt> -o ./eds/ 2> errorfile.txt
```
This output to stderr contains the graphs in dot format that were not 
decomposable. The `visualize.py` script extracts those dot formatted graphs and 
actually uses dot to convert them into more readable pdfs (or pngs if you like).
Moreover, this script prints out the concrete sentences belonging to the 
non-decomposable graphs as well as the number of tokens 
(useful to search for minimal not-working examples).  
*Version 2*: Added extraction and print out of the predicted constants of 
non-decomposable graphs.  
*Version 3*: Adapted to new input format which also provides EDS id. This id is
also extracted.  

Note: If the above `createCorpus` command results in a java `OutOfMemoryError`
you might want to allow java to occupy more memory.  If you add the option 
`-Xmx3g`, java can use up to `3g`(i.e. 3GB) of memory.


## How to run
- **only tested with EDS so far** (see `CreateCorpus.java` of EDS for how error
 file, i.e. input of this script, needs to be formatted)
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
`python3 visualize.py --input INPUTFILE --outputdir OUTPUTDIR`
or if you would like another output format than PDF use  
`python3 visualize.py --input INPUTFILE --outputdir OUTPUTDIR --outformat OUTFORMAT`


### Example usages
(requires specific directory structure)  
`python3 visualize.py --input ../visualizer_data/input/eds/errfile.txt --outdir ../visualizer_data/output/eds --outformat pdf`
`python3 visualize.py --input ../visualizer_data/input/eds/errfile.txt --outdir ../visualizer_data/output/eds --outformat png`


### Input
File with all error messages (everything that was redirected from stderr to
 a file) from the `CreateCorpus` call.

### Output
- a `sentlength.log` file in tab separated format:  
  for each non-decomposable graph prints one line:  
  `INFO<TAB>not decomposable<TAB>EDSID<TAB>SENTNO<TAB>SENTLENGTH<TAB>LINENUMBER<TAB>SENTENCE`  
  e.g. `INFO  not decomposable  id 20010002  004  4  826  Not this year .`  
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
