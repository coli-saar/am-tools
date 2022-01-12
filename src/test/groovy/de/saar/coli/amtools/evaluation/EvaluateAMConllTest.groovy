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
        SGraph gold = new GraphAlgebra().parseString("(u_3 / yearn-01  :ARG0 (u_1 / dragon  :mod (u_4 / little)  :ARG0-of (u_2 / fly-01  :ARG1-of u_3)))")

        print(result.toIsiAmrString())

        assert gold.equals(result)

    }

}