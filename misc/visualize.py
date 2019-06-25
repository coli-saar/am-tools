# -*- coding: utf-8 -*-
# visualize.py
# Hiwi 2019 - visualize non-decomposable graphs (using graphviz dot)
# author                Pia Weißenhorn
# 1st email address     piaw@coli.uni-saarland.de
# 2nd email address     s8piweis@stud.uni-saarland.de
# last changed          25.06.2019
# OS                    Windows 8.1
# Python                3.7
# need Penman package (tested with Penman-0.6.2 )
# Given the stderr output of CreateCorpus extract the non-decomposable graphs
# and call graphviz dot to transform dot files to PDFs or other more readable
# file format.
# Note:INSTALLED  Penman-0.6.2 docopt-0.6.2  https://pypi.org/project/Penman/

# TODO: extract more information: other exceptions plus their graphs
# TODO: clean up code (main function too long, especially loop for-line-in-file
# TODO: stuff
# TODO: ? problem & and other html reserved token in label of node
# TODO: check correctness penmam to dot conversion

import sys         # for exit
import os          # os.path.isfile ...
import subprocess  # call external commands like `dot` from graphviz tool
import logging     # log file
import argparse    # parse command line options
import penman      # extract penman formatted constants
import re          # extract sources
from timeit import default_timer as dtimer

# todo: change filename of log?
logging.basicConfig(filename="sentlength.log", filemode="w",
                    level=logging.DEBUG, format="%(levelname)s\t%(message)s")
logging.info("\t".join(["SENTNO", "SENTLENGTH", "LINENUMBER", "SENTENCE"]))
# sentence number is 1 for first non-decomposable sentence...

# OUTPUTPREFIX = "ndvis_"  # todo: make this command line arg?
OUTPUTPREFIX = ""


# for printing to stderr
def eprint(*args, **kwargs):
    """prints to standard error stream (use it as normal print)"""
    print(*args, file=sys.stderr, **kwargs)


def string_to_list(line: str) -> list:
    """
    converts string with list of tokens in it to list of tokens
    :param line: string like 'not decomposable [Token1, Token2, Token3]'
    :return: list of tokens
    >>> string_to_list("not decomposable [Not, this, year, .]")
    ['Not', 'this', 'year', '.']
    >>> string_to_list("not decomposable [Hello, New York, !]")
    ['Hello', 'New York', '!']
    >>> string_to_list("not decomposable [a, b, c, d]")
    ['a', 'b', 'c', 'd']
    >>> string_to_list("not decomposable [ a, b , c, d ]")
    [' a', 'b ', 'c', 'd ']
    >>> string_to_list("not decomposable [a, ,, b, c, d]")
    ['a', ',', 'b', 'c', 'd']
    """
    line = line.rstrip("\n")
    line = line.replace("not decomposable ", "")
    # todo: convert assertion to if-statement (raise Exception or log warning?)
    assert(len(line) > 2 and line[0] == "[" and line[-1] == "]")
    line = line[1:-1]  # remove '[' and ']'
    toks = line.split(", ")
    return toks


def get_anno(node: str) -> tuple:
    """
    get pair of node name and annotation
    
    :param node: string like 'hello<world>'
    :return: pair with nodename and annotation like 'hello', 'world'
    >>> get_anno(node="hello<world>")
    ('hello', 'world')
    >>> get_anno(node="helloworld")
    ('helloworld', '')
    >>> get_anno(node="he_llo<wo_.rld>")
    ('he_llo', 'wo_.rld')
    """
    if "<" in node and ">" in node:
        matchobj = re.fullmatch(pattern="(.*)<(.*)>", string=node)
        if matchobj is None:
            eprint("Error during regex matching for node ", node)
            return node, ""
        else:
            nodename = matchobj.group(1)
            anno = matchobj.group(2)
            return nodename, anno
    return node, ""


def penman2dotstring(graphstr: str, typestr: str) -> str:
    """
    Convert graph in penman notation to dot notation
    
    :param graphstr: string with penman notation for graph
    :param typestr: type of graph (source annotation)
    :return: dot formatted string of that input graph
    """
    # todo: penman: special version for amr?
    # todo test: any information lost?
    # todo: what if error? empty graph, ...
    g = penman.decode(graphstr)
    gtriples = g.triples()
    graphstr = "digraph " + " {\n"
    # graphstr = "subgraph cluster_{}".format(id) + " {\n"
    intend = "  "
    graphstr += intend + "bgcolor=\"transparent\" ; rankdir = \"TD\"\n"
    graphstr += intend + "label = \"Graphtype: {}\"\n".format(typestr)
    # todo: does dot complain when there is a unknown node in edge definition?
    # todo: what does dot do if node label only introduced later?
    for src, relation, target in gtriples:  # Triple(source, relation, target)
        if relation == "instance":  # this triple is a node
            # src: node name, relation: 'instance', target: node label
            # note: node label can be None
            srcnodename, srcanno = get_anno(src)
            graphstr += intend + srcnodename
            # print label
            targetstr = str(target).strip("\"")
            targetstr = targetstr if targetstr != str(None) else ""
            # change format to <labelname <BR/><FONT COLOR="red">ROOT</FONT>>
            # todo: check if labelname or annotation contain html special chars?
            # graphstr += " [ label= \"{}\n{}\" ]".format(targetstr, srcanno)
            if srcanno != "":
                targetstr = targetstr.replace("&", "&amp;")
                graphstr += " [ label=<{} <BR/><FONT COLOR=\"red\">{}" \
                            "</FONT>>]".format(targetstr, srcanno)
            else:  # no annotation
                graphstr += " [ label= \"{}\" ]".format(targetstr)
        else:  # triple is an edge
            # src: from-node , relation : edge label, target: to-node
            srcnodename, srcanno = get_anno(src)
            trgnodename, trganno = get_anno(target)
            graphstr += intend + "{} -> {} [ label = \"{}\" ]".format(
                srcnodename, trgnodename, relation)
        graphstr += "\n"
    graphstr += "}\n"
    return graphstr


def get_graph_type_pair(line: str) -> list:
    """
    Get penman string and type of graph as pair
    
    :param line: line with penman graph with type in it
    :return: (penman-string, type)
    >>> get_graph_type_pair("[(a / neg  :lnk (b / S)  :ARG1 (c))--TYPE--(s())]")
    [('(a / neg  :lnk (b / S)  :ARG1 (c))', '(s())')]
    >>> get_graph_type_pair("[(x9<root> / _year  :lnk (exu / S))--TYPE--()]")
    [('(x9<root> / _year  :lnk (exu / S))', '()')]
    >>> get_graph_type_pair("[(x / _x  :lnk (y / _y))--TYPE--(), (z / _z  :lnk (a / _a))--TYPE--()]")
    [('(x / _x  :lnk (y / _y))', '()'), ('(z / _z  :lnk (a / _a))', '()')]
    """
    line = line.rstrip("\n")
    assert(len(line) >= 2)  # at least []
    assert (line[0] == "[" and line[-1] == "]")
    line = line[1:-1]  # delete leading [ and trailing ]
    if len(line) == 2:
        return [("", "")]  # todo what to return in case of empty graph?
    typeseparator = "--TYPE--"
    graphseparator = ", "  # assumes that type doesn't contain a whitespace,
    # otherwise this would also split types like (s(), o())
    assert(typeseparator in line)
    graphs = line.split(graphseparator)
    graphpairs = list()
    for graph in graphs:
        pair = graph.split(typeseparator)  # graphstr, typestr
        assert(len(pair) == 2)
        graphpairs.append(tuple(pair))
    return graphpairs


def main():
    # 1. get cmd arguments
    defaultoutformat = "pdf"
    optparser = argparse.ArgumentParser(
        description="extract non-decomposable graphs and visualize them")
    optparser.add_argument("--input", dest="inputfile", type=str,
                           help="Path to input file (errors of CreateCorpus)")
    optparser.add_argument("--outdir", dest="outputdir", type=str,
                           help="Path to output directory")
    optparser.add_argument("--outformat", dest="outformat", type=str,
                           help="Outputformat of dot (default: {})".format(
                               defaultoutformat))
    opts = optparser.parse_args()
    inputfile = opts.inputfile
    outputdir = opts.outputdir
    outformat = opts.outformat
    suffix_const = "_const_"
    print("->       INPUT FILE: ", inputfile)     # debug
    print("-> OUTPUT DIRECTORY: ", outputdir)     # debug
    print("->    OUTPUT PREFIX: ", OUTPUTPREFIX)  # debug
    print("->    OUTPUT FORMAT: ", outformat)     # debug
    
    # 2. Check if input meets requirements
    allowed_formats = ['pdf', 'png', 'jpg', 'eps', 'svg', 'bmp']
    assert(defaultoutformat in allowed_formats)
    # see https://www.graphviz.org/doc/info/output.html for all formats and
    # feel free to add some of them to allowed_formats
    if outformat not in allowed_formats:
        eprint("Specified output format is currently not allowed!")
        eprint("{} - not valid, use default: {}".format(
            outformat, defaultoutformat))
        eprint("Allowed formats: ", " ".join(allowed_formats))
        outformat = defaultoutformat
    outputdir = outputdir + "/" if not outputdir.endswith("/") else outputdir
    if not os.path.isfile(inputfile):
        eprint("Not a valid file path: ", inputfile)
        sys.exit(2)
    if not os.path.isdir(outputdir):
        eprint("Not a valid directory path: ", outputdir)
        sys.exit(2)
    
    # 3. Read input file ...
    nondecomp_cnt = 0
    linenumber = 0
    dotinputdir = outputdir + "dot/"
    dotoutputdir = outputdir + outformat + "/"
    for dotdir in [dotinputdir, dotoutputdir]:
        if not os.path.exists(dotdir):
            os.mkdir(dotdir)
    
    starttime = dtimer()
    inside_nondecomp = False
    # todo: adapt: also other graphs now id!
    with open(file=inputfile, mode="r") as infile:
        lines_for_curr_graph = list()
        state = 0  # 0: not inside graph, 1: non-dec, 2: const, 3: digraph
        sent_file_name_infix = None
        current_sentence = list()
        current_id = None
        lastword = None
        lastwordcnt = 0
        sent_lengths = list()
        for line in infile:
            linenumber += 1
            if linenumber % 10000 == 0:  # show progress
                sys.stdout.write(".")
                sys.stdout.flush()
            if line.startswith("id "):
                current_id = line.rstrip("\n")
                continue
            if line.startswith("not decomposable"):  # new graph found!
                assert(not inside_nondecomp)
                inside_nondecomp = True
                assert(state == 0)
                # line is like:   not decomposable [Token1, token2, token3]
                current_sentence = string_to_list(line)
                sent_lengths.append(len(current_sentence))
                nondecomp_cnt += 1
                sent_file_name_infix = str(nondecomp_cnt).zfill(3)  # 001
                state = 2  # expect constants next
                lastword = None
                lastwordcnt = 0
                continue
            if line.startswith("=====end not decomposable====") and inside_nondecomp:
                inside_nondecomp = False
            if not inside_nondecomp:
                continue
            newgraph_begins = line.startswith("digraph")
            oldgraph_ends = (line.strip() == "}")  # last line of graph
            if state == 2 and not newgraph_begins:  # alignment:
                if lastword is None:  # expect word
                    lastword = line.rstrip("\n")
                    lastwordcnt += 1
                else:  # expect graph constants for the last word
                    assert(lastword is not None)
                    graphpairs = get_graph_type_pair(line)
                    # filename   001_const_8_lastword   8th word seen
                    filename = sent_file_name_infix + suffix_const + \
                        str(lastwordcnt) + "_" + lastword.replace("/", "~")
                    gid = 0  # graph id
                    # for each graph that was predicted for the last word
                    # convert the penman style graphstr into dot format and
                    # print it to dot and then call graphviz
                    # Those graphs are numbered to avoid overwriting already
                    # existing file/graph for lastword
                    for graphstr, typestr in graphpairs:
                        gid += 1
                        graphdotstr = penman2dotstring(graphstr, typestr)
                        dotinfile = \
                            dotinputdir + filename + "_" + str(gid) + ".dot"
                        with open(dotinfile, mode="w",
                                  encoding="utf-8") as dotf:
                            dotf.write(graphdotstr)
                        # 2. call dot to create png or pdf or whatever OUTFORMAT
                        # file and write output to other file
                        call = ["dot", "-T" + outformat, "-Gdpi=300",
                                "-o", dotoutputdir + OUTPUTPREFIX + filename +
                                "_" + str(gid) + "." + outformat,
                                dotinfile
                                ]
                        try:
                            subprocess.run(call, check=True,
                                           capture_output=False)
                        except subprocess.CalledProcessError as e:
                            eprint("WARNING: Called process error!\n", str(e))
                    # write to file: first tried to write all to one file, but
                    # dot only outputs the first (at least pdf, png, not ps)
                    # then decided for subgraphs but doesn't work because of
                    # overlapping labels...
                    lastword = None  # prepare for next alignment
                continue
            if newgraph_begins:  # increase counter, log sentence
                state = 3
                sent = " ".join(current_sentence)
                sentence_length = len(current_sentence)
                # todo: evtl log name and path of dot outfile?
                logging.info("\t".join(["not decomposable", current_id,
                                        sent_file_name_infix,
                                        str(sentence_length),
                                        str(linenumber),
                                        sent]))
            if state == 3:
                # add current line to lines for current graph
                # (including if oldgraph_ends is True or newgraph_begins is
                # True!)
                lines_for_curr_graph.append(line)
            if oldgraph_ends:
                assert(state == 3)
                # 1. write graph to dot file (overwrites existing one!)
                filename = sent_file_name_infix
                dotinputfile = dotinputdir + filename + ".dot"
                # (or write dot only to 1 temp file?)
                assert(len(lines_for_curr_graph) >= 2)
                with open(dotinputfile, mode="w", encoding="utf-8") as dotf:
                    for i, l in enumerate(lines_for_curr_graph):
                        # write caption (value: sentence) just before last '}'
                        if i == len(lines_for_curr_graph)-1:
                            # does parsing of dot crash when sentence
                            # contains weird characters?
                            dotf.write(
                                "graph [ label=\"{}\", labelloc=top]".format(
                                    " ".join(current_sentence)))
                        # write line
                        dotf.write(l)
                # 2. call dot to create png or pdf or whatever OUTFORMAT file
                #    and write output to other file
                call = ["dot", "-T" + outformat, "-Gdpi=300",
                        "-o", dotoutputdir + OUTPUTPREFIX + filename +
                        "." + outformat,
                        dotinputfile
                        ]
                try:
                    subprocess.run(call, check=True, capture_output=False)
                except subprocess.CalledProcessError as e:
                    eprint("WARNING: Called process error!\n", str(e))
                # 3. clear data structures for next non-decomposable graph
                lines_for_curr_graph = list()
                current_sentence = list()
                state = 0
        # todo: what happens when last line read and still inside graph?
        assert(state == 0)
        assert(len(lines_for_curr_graph) == 0)
    endtime = dtimer()
    sys.stdout.write("\n")
    print("INFO: Took {:.3f} seconds".format(endtime - starttime))
    print("INFO: {} Non-decomposable graphs found".format(nondecomp_cnt))
    if len(sent_lengths) != 0:
        print("INFO: Sentence length: Mean: {:.3f} , Min: {}, Max: {}".format(
            sum(sent_lengths) / len(sent_lengths),
            min(sent_lengths), max(sent_lengths)
        ))
    
    return


if __name__ == "__main__":
    main()
