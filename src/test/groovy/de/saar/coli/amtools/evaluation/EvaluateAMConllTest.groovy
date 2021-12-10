package de.saar.coli.amtools.evaluation

import de.up.ling.irtg.algebra.graph.GraphAlgebra
import de.up.ling.irtg.algebra.graph.SGraph
import org.apache.tools.ant.types.Commandline
import org.junit.Test


class EvaluateAMConllTest {


    @Test
    public void testDefaultEvaluation() {

        String args = "-c examples/evaluation_input/toyAMR.amconll -o examples/output/";

        EvaluateAMConll.main(Commandline.translateCommandline(args))

        BufferedReader br = new BufferedReader(new FileReader("examples/output/parserOut.txt"))
        SGraph result = new GraphAlgebra().parseString(br.readLine())
        SGraph gold = new GraphAlgebra().parseString("(u_3 / \"LEX@3\"  :ARG0 (u_1 / \"LEX@2\"  :mod (u_4 / \"LEX@1\")  :ARG0-of (u_2 / \"LEX@5\"  :ARG1-of u_3)))")

        assert gold.equals(result)

    }

}