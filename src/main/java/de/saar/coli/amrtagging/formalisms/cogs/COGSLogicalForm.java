package de.saar.coli.amrtagging.formalisms.cogs;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


// TODO: question: should the logical form contain the input sentence in order to ground the indices?
// TODO: use a parser generator for parsing (antlr?) instead of my DIY parser (future me, please forgive me)
// TODO: test parsing
/**
 * Class for the logical forms used in COGS (parsing into them, accessing parts of them)<br>
 *
 * The COGS dataset and the corresponding logical forms are introduced in
 * Kim and Linzen (<a href="https://www.aclweb.org/anthology/2020.emnlp-main.731/">2020</a>).
 * The logical forms are postprocessed versions of
 * Reddy et al. (<a href="https://www.aclweb.org/anthology/D17-1009/">2017</a>)).<br>
 * Setting the case of so-called 'primitives' aside, a COGS logical form consist of a possibly empty prefix of iotas
 * followed by a conjunction of one or more conjuncts.
 * Iotas are used for definite descriptions and look like this: <code>* boy ( x _ 1 ) ;</code><br>
 * The core of each logical form are the terms. A term consist of a predicate name and 1 or 2 arguments.
 * A predicate name, in turn, consists of up to 3 parts, the first part is always a lemma.
 * Terms with a 1-part predicate name can always have 1 argument, all others (2- or 3-part name) have 2 arguments.
 * Arguments can be proper names, indices (<i>x_i</i>) or lambda variables, but lambda variables are only allowed to
 * appear in primitives (todo do we check this?).
 * For non-primitives the first argument of each term is always an index which points to the word whose lemma equals the
 * first part of the predicate name.<br>
 * Primitives are either single proper names like <i>Ava</i> or conjunctions with a non-empty prefix of lambda variables.
 * Instead of indices, primitives contain lambda variables as arguments (even for the first argument).
 *
 * @author piaw (weissenh)
 * April 2021
 */
public class COGSLogicalForm {
    public enum AllowedFormulaTypes {IOTA, LAMBDA, NAME};
    private AllowedFormulaTypes formulaType;
    List<String> lfTokens;  /// raw list of tokens of the formula todo maybe delete?
    List<Term> prefixTerms;  ///< iota terms if type is IOTA otherwise remains null
    List<Term> conjuncts;   ///< conjunct terms (iota and lambda)
    List<Argument> lambdas;  ///< lambda variables if type is LAMBDA, otherwise remains null
    Argument namePrimitive;   ///< name primitive (proper name) if type is NAME otherwise remains null

    // ---------Constructors ---------------------------------------------------
    public COGSLogicalForm(List<String> logicalFormTokens) {
        this.lfTokens = Objects.requireNonNull(logicalFormTokens);
        parseLogicalForm(this.lfTokens);
    }

    public COGSLogicalForm(List<Term> iotas, List<Term> conjuncts) {
        Objects.requireNonNull(iotas);
        Objects.requireNonNull(conjuncts);
        if (conjuncts.size()== 0) {
            throw new IllegalArgumentException("List of conjunct terms need to be non-empty!");
        }
        setFormulaType(AllowedFormulaTypes.IOTA);
        this.prefixTerms = sortTerms(iotas);
        this.conjuncts = sortTerms(conjuncts);
        // todo check that this works and lfTokens contains reasonable tokens?
        this.lfTokens = Arrays.asList(getStringFromTerms().split(" "));
    }

    // todo I couldn't do List<Argument> because of a class with the above constructor using List<Term> iota: type erasure problem
    // right now I have to cast the lambdas back and forth (array vs list), that's really ugly and should be changed
    // todo little bit duplicated code for lambda and iota constructor (conjuncts)
    public COGSLogicalForm(Argument[] lambdas, List<Term> conjuncts) {
        Objects.requireNonNull(lambdas);
        Objects.requireNonNull(conjuncts);
        if (conjuncts.size() == 0) {
            throw new IllegalArgumentException("List of conjunct terms need to be non-empty!");
        }
        if (lambdas.length == 0) {
            throw new IllegalArgumentException("We need at least one lambda Argument for the prefix!");
        }
        for (Argument a: lambdas) {
            if (!a.isLambdaVar()) {
                throw new IllegalArgumentException("Arguments must be lambda variables! (e.g. no indices/names!)");
            }
        }
        setFormulaType(AllowedFormulaTypes.LAMBDA);
        List<Argument> lvs = Arrays.asList(lambdas);
        lvs.sort(Comparator.comparing((Argument a) -> a.raw));  // sort lambda variables by raw string:  a < b < e
        this.lambdas = lvs;
        this.conjuncts = sortTerms(conjuncts);
        // todo check that this works and lfTokens contains reasonable tokens?
        this.lfTokens = Arrays.asList(getStringFromTerms().split(" "));
    }


    /*
    // DEBUG: used for debugging the parsing functionality
    /// creates a dummy logical form
    public COGSLogicalForm() {
        // The boy wanted to go .
        // * boy ( x _ 1 ) ; want . agent ( x _ 2 , x _ 1 ) AND want . xcomp ( x _ 2 , x _ 4 ) AND go . agent ( x _ 4 , x _ 1 )
        String lfStr = "* boy ( x _ 1 ) ; want . agent ( x _ 2 , x _ 1 ) AND want . xcomp ( x _ 2 , x _ 4 ) AND go . agent ( x _ 4 , x _ 1 )";
        // String lfStr = "LAMBDA a . LAMBDA b . LAMBDA e . giggle . agent ( e , a ) AND giggle . theme ( e , b )";
        // String lfStr = "LAMBDA a . LAMBDA b . LAMBDA e . giggle . agent ( e , a )";
        this.lfTokens = Arrays.asList(lfStr.split(" "));
        parseLogicalForm(lfTokens);
    }
     */

    //
    // ---------methods --------------------------------------------------------
    //

    // get type of formula: name/primitive, iota
    public AllowedFormulaTypes getFormulaType() { return formulaType; }
    // get conjuncts todo what if name: null
    public List<Term> getConjunctionTerms() { return conjuncts; }
    // get Prefix terms, which is iotas or null in case of primitives  todo what if name: null
    public List<Term> getPrefixTerms() { return prefixTerms; }

    // todo getAllTerms: should it be a list or a set? what to return for NAME ?
    /**
     * Note: if it's a proper name, returns an empty list todo ok?
     * For non-primitive the lists of prefix terms and terms in the conjunction are returned together in one list.
     *
     * @return all <code>Term</code>s of the logical form
     */
    public List<Term> getAllTerms() {
        switch (getFormulaType()) {
            case NAME:
                return new ArrayList<>();
            case LAMBDA:
                return getConjunctionTerms();
            case IOTA:
                return Stream.concat(getPrefixTerms().stream(), getConjunctionTerms().stream())
                        .collect(Collectors.toList());
            default:
                assert(false); //no other types
                return null;
        }
    }

    /** Remember arguments can be indices, proper names or lambda variables: the last two can appear in primitives!
     * @return set of all <code>Argument</code>s in the logical form (so duplicates removed)
     */
    public Set<Argument> getArgumentSet() {
        Set<Argument> argset = new HashSet<>();
        switch (getFormulaType()) {
            case NAME:
                argset.add(this.namePrimitive);
                break;
            case LAMBDA:
                argset.addAll(lambdas);
                break;
            case IOTA:
                for (Term t: prefixTerms) { argset.addAll(t.getArguments()); }
                for (Term t: conjuncts) { argset.addAll(t.getArguments()); }
                break;
            default:
                assert(false); //no other types
        }
        return argset;
    }

    public Argument getNamePrimitive() {
        if (getFormulaType() != AllowedFormulaTypes.NAME) {
            // could just return null, but hopefully this exception is more informative than a NullPointerException
            throw new RuntimeException("This logical form is not a name primitive, can't return a reasonable value!");
        }
        return namePrimitive;
    }

    /**
     * If we had indices, we would know which Argument points to the word. But since we have only uninformative lambda
     * variable names, we have to pick one based on which is the first argument in the first (and all following) terms
     * in tha formula.
     * Here are a few examples:<br>
     * - <i>touch</i> and <code>LAMBDA a. LAMBDA b.LAMBDA e. touch.agent(e,a) AND touch.theme(e,b)</code>: <i>e</i> is the argument we return<br>
     * - <i>giggle</i> and <code>LAMBDA a.LAMBDA e. giggle.agent(e,a)</code>: <i>e</i> is the argument we return<br>
     * - <i>ball</i> and <code>LAMBDA a. ball(a)</code>: <i>a</i> is the argument we return<br>
     *
     * @return one of the lambda <code>Argument</code>s. the one that is said to correspond to the only sentence token
     */
    public Argument getLexicalArgumentForLambda() {
        if (getFormulaType() != AllowedFormulaTypes.LAMBDA) {
            // could just return null, but hopefully this exception is more informative than a NullPointerException
            throw new RuntimeException("This logical form is not a lambda primitive, can't return a reasonable value!");
        }
        // I don't want to rely on the first element of `this.lambdas`: might change how we inserted the lambdas...
        Argument lexicalarg = null;
        for (Term t: getAllTerms()) {
            // todo check with assert that for all terms this is the same argument
            Argument tmp = t.getArguments().get(0); // first argument is always the 'lemma'/'lexical' one
            if (lexicalarg == null) {
                lexicalarg = tmp;
            }
            else {
                assert(lexicalarg.equals(tmp));  // should always have same first argument, otherwise semantically odd
            }
        }
        assert(lexicalarg != null); // we assume that a LAMBDA form has at least one Term
        return lexicalarg;
    }

    // todo get predicate names
    public String toString() { return String.join(" ", lfTokens); }

    private void setFormulaType(AllowedFormulaTypes type) { formulaType = Objects.requireNonNull(type); }

    //
    // -----Parsing ----------------------------------------------------------------
    //
    private List<Argument> parseArguments(List<String> tokens) {
        int toklength = tokens.size();
        assert(toklength > 0);
        int current_idx = 0;
        List<Argument> args_accumulator = new ArrayList<>();
        Argument currentArg = null;
        String current_token;
        boolean expect_comma_next = false;
        for (;current_idx < toklength; current_idx++) {
            current_token = tokens.get(current_idx);
            if (current_token.equals(",")) {
                assert(expect_comma_next);
                assert(currentArg != null);
                currentArg = null;
                expect_comma_next = false;
            }
            else {
                if (!current_token.equals("x") && !current_token.equals("_")) {
                    if (current_token.equals(")")) {
                        assert(current_idx == toklength-1);
                        continue;
                    }
                    assert(!expect_comma_next);
                    assert(currentArg == null);
                    currentArg = new Argument(current_token);
                    args_accumulator.add(currentArg);
                    expect_comma_next = true;
                    // have flag expect comma next
                }
                // do nothing of token is 'x' or '_'
            }
        }
        assert(expect_comma_next);
        return args_accumulator;
    }

    private Term parseIota(List<String> tokens) {
        // 0=* , 1=predicatenoun, 2=( , 3=x, 4=_, 5=int, 6=), 7=;
        assert(tokens.size() == 8);
        assert(tokens.get(0).equals("*"));
        assert(tokens.get(2).equals("("));
        assert(tokens.get(3).equals("x"));
        assert(tokens.get(4).equals("_"));
        assert(tokens.get(6).equals(")"));
        assert(tokens.get(7).equals(";"));
        List<String> pred = tokens.subList(1, 2);
        //List<String> args = tokens.subList(5, 6);
        List<Argument> args = parseArguments(tokens.subList(3, 6));  // x _ int
        return new Term(pred, args);
    }

    private int parseIotas(List<String> tokens) {
        // returns index to continue with rest of list todo this is a bad choice
        int length = tokens.size();
        int len_of_iota = 8;  // 0=* , 1=predicatenoun, 2=( , 3=x, 4=_, 5=int, 6=), 7=;
        int current_idx = 0;
        String current_token;
        List<Term> iotas = new ArrayList<>();
        // iota (list of terms)
        // conjunct (list of terms)

        // 1. get iotas
        while (true) {
            current_token = tokens.get(current_idx);
            if (!current_token.equals("*")) {
                break;
            }
            assert(current_idx+len_of_iota < length);
            Term t = parseIota(tokens.subList(current_idx, current_idx+len_of_iota));
            iotas.add(t);
            current_idx += len_of_iota;
        }
        this.prefixTerms = iotas;
        return current_idx;
    }

    private Term parseConjunct(List<String> tokens) {
        int toklength = tokens.size();
        // assert(toklength >= 6);  // minimally 0=boy 1=( 2=x 3=_ 4=int 5=)  // todo doesn't work with lambda
        assert(toklength >= 4);
        assert(tokens.get(toklength - 1).equals(")"));
        // todo assert(tokens contains "(")
        int current_idx = 0;
        List<String> pred_name_accumulator = new ArrayList<>();
        while (!tokens.get(current_idx).equals("(") && current_idx < toklength) {
            if (tokens.get(current_idx).equals(".")) {
                current_idx += 1;
                continue;
            }
            pred_name_accumulator.add(tokens.get(current_idx));
            current_idx += 1;
        }
        // done with predicate
        assert(tokens.get(current_idx).equals("("));
        current_idx +=1; // we can skip the '('
        assert(current_idx < toklength);
        // now arguments
        List<Argument> args_accumulator = parseArguments(tokens.subList(current_idx, toklength));
        return new Term(pred_name_accumulator, args_accumulator);
    }

    private List<Term> parseConjunction(List<String> tokens, int currentIdx) {
        int length = tokens.size();
        assert(currentIdx < length);
        // todo split by 'AND' (possible not present)  groupBy???/stream ?
        // todo test this parse function and others
        assert(tokens.get(length - 1).equals(")"));
        String current_token = null;
        List<Term> conjuncts = new ArrayList<>();
        ArrayList<String> current_conjunct = new ArrayList<>();
        for (;currentIdx < length; currentIdx++) {
            current_token = tokens.get(currentIdx);
            if (current_token.equals("AND")) {
                assert(current_conjunct.size()>0);
                Term t = parseConjunct(current_conjunct);
                conjuncts.add(t);
                current_conjunct = new ArrayList<>();
            }
            else {
                current_conjunct.add(current_token);
            }
        }
        // add last conjunct?
        Term t = parseConjunct(current_conjunct);
        conjuncts.add(t);
        return conjuncts;
    }

    private void parseLogicalForm(List<String> tokens) throws RuntimeException {
        // todo for now assert valid logical form, todo maybe use parser generator like antlr?
        int length = tokens.size();
        if (length == 0) {
            throw new RuntimeException("Parser Error for this formula: " + String.join(" ", tokens));
        }
        if (length == 1) {
            // assume primitive (to do: check)
            // todo check
            setFormulaType(AllowedFormulaTypes.NAME);
            assert(tokens.get(0).matches("[A-Z][a-z]+"));  // proper name
            this.namePrimitive = new Argument(tokens.get(0));
            return;
        }
        assert(length > 0);
        String first_token = tokens.get(0);
        // boolean has_prefix = true;
        if (first_token.equals("LAMBDA")) {
            setFormulaType(AllowedFormulaTypes.LAMBDA);
            // todo do check this
            int current_idx = 0;
            int len_of_lambda = 3; // 0=LAMBDA 1=[a,b,e] 2=.
            String current_token;
            List<Argument> lambda_vars = new ArrayList<>();
            // 1. get iotas
            while (true) {
                current_token = tokens.get(current_idx);
                if (current_token.equals("LAMBDA")) {
                    assert(current_idx+len_of_lambda < length);
                    lambda_vars.add(new Argument(tokens.get(current_idx + 1)));   // current_idx is LAMBDA, next is lambda-var (a,b,e)
                    current_idx += len_of_lambda;
                }
                else {
                    break;
                }
            }
            assert(lambda_vars.size() <= 3); // not more than 3 vars possible in dataset? a,b,e
            this.lambdas = lambda_vars;
            // 2. parse conjunction  // todo problem: variables are a,e,b, : treated as names: what to do?????
            this.conjuncts = parseConjunction(tokens, current_idx);
            return;
        }
        else {
            setFormulaType(AllowedFormulaTypes.IOTA);
        }
        // iota only (possibly empty prefix
        int current_idx = 0;
        // 1. get iotas
        current_idx = parseIotas(tokens);
        // 2. parse conjunction
        // assert * not in rest of tokens
        this.conjuncts = parseConjunction(tokens, current_idx);
    }

    /// sorts terms based on indices and returns list sorted: todo test sorting
    private List<Term> sortTerms(List<Term> terms) {
        Objects.requireNonNull(terms);
        // if list has 0 or only 1 element, it's trivially sorted, so can directly return
        if (terms.size() < 2) { return terms; }
        terms.sort(new Term.TermComparer());
        return terms;
    }

    // todo magic strings all over
    // todo test this function
    /// convert the logical form to a string based on the Terms (conjuncts, iotas) and Arguments (lambda vars, name)
    private String getStringFromTerms() {
        assert(formulaType != null);
        AllowedFormulaTypes type = getFormulaType();
        if (type == AllowedFormulaTypes.NAME) { return namePrimitive.toString(); }
        else {  // not a NAME: IOTA or LAMBDA
            StringBuilder sb = new StringBuilder();
            // handle formula type specific prefix
            if (type == AllowedFormulaTypes.IOTA) {
                for (Term t: this.prefixTerms) {
                    sb.append("* ");
                    sb.append(t.toString());
                    sb.append(" ; ");
                }
            }
            else if (type == AllowedFormulaTypes.LAMBDA) {
                for (Argument a: this.lambdas) {
                    sb.append("LAMBDA ");
                    sb.append(a.toString());
                    sb.append(" . ");
                }
            }
            else { throw new RuntimeException("new formula type added but forgot to change this method mb?"); }
            // common conjunction:
            int conjunctionSize = this.conjuncts.size();
            for (int i = 0; i < conjunctionSize; i++) {
                Term t = this.conjuncts.get(i);
                sb.append(t.toString());
                if (i != conjunctionSize-1) {  // for all except last conjunct: add AND
                    sb.append(" AND ");
                }
            }
            return sb.toString();
        }
    }

    //
    // --------- inner classes -------------------------------------------------
    //

    /**
     * An argument is either an index (<i>1</i>), a lambda variable (<i>a</i>) or a proper name (<i>Ava</i>)<br>
     *
     * If you pass a numerical string, it is assumed to be an index, otherwise it is assumed to be either a lambda
     * variable (single letter a,b,e ) or a proper name (First letter uppercase, followed by lowercase chars).<br>
     * The index (if present) is converted to an integer, otherwise it remains invalid (i.e. -1)
     */
    public static class Argument {
        // todo precompile regex patterns?
        private enum AllowedArgumentTypes {INDEX, PROPERNAME, LAMBDAVAR};
        private final AllowedArgumentTypes argumentType;
        private final String raw;
        private int index = -1;

        public Argument(int index) {
            if (index < 0) {
                throw new IndexOutOfBoundsException("Indices must be non-negative!");
            }
            this.raw = String.valueOf(index);
            this.index = index;
            this.argumentType = AllowedArgumentTypes.INDEX;
        }

        public Argument(String token) {  // shouldn't throw NumberFormatException, but maybe does?
            this.raw = Objects.requireNonNull(token);
            if (token.matches("[0-9]+")) {
                argumentType = AllowedArgumentTypes.INDEX;
            }
            else if (token.matches("[A-Z][a-z]+")) {
                argumentType = AllowedArgumentTypes.PROPERNAME;
            }
            else if (token.matches("[abe]")) {
                argumentType = AllowedArgumentTypes.LAMBDAVAR;
            }
            else {
                throw new IllegalArgumentException("Couldn't obtain argument from string '"+token+"': " +
                        "doesn't fit required format.");
            }
            if (argumentType == AllowedArgumentTypes.INDEX) {
                this.index = Integer.parseInt(token);
            }
        }

        public boolean isIndex() { return argumentType == AllowedArgumentTypes.INDEX;}
        public boolean isLambdaVar() { return argumentType == AllowedArgumentTypes.LAMBDAVAR;}
        public boolean isProperName() { return argumentType == AllowedArgumentTypes.PROPERNAME;}
        public int getIndex() { return index; }  // only call that if you know that it is a proper index
        public String getName() { return raw; }
        public String toString() {
            if (isIndex()) { return "x _ "+raw; } // todo magic string "x _ "
            return raw;
        }

        @Override
        public int hashCode() {
            // we use the hash of the raw string.
            // all other attributes of this class were deterministically derived from this in the constructor.
            // if two Argument objects don't contain the same string, they can never be equal to each other.
            return this.raw.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Argument other = (Argument) obj;
            // if two Argument objects don't contain the same string, they can never be equal to each other.
            // if they contain the same string, then they are equal
            // (`raw` determines how all other attributes are set in the constructor)
            return raw.equals(other.raw);
        }

    }

    /**
     * Predicate is used for a predicate name (shouldn't be confused with Term), e.g. <i>want.agent</i> <br>
     *
     * A predicate name may consists of different parts (1 of up to 3 parts allowed), <br>
     * e.g. <i>boy</i>, <i>want.agent</i> or <i>cookie.nmod.beside</i>.<br>
     * The tokens in the constructor shouldn't contain the punctuation chars as separators
     * todo: maybe change confusing classname: predicate to PredicateName ?
     */
    public static class Predicate {
        private final List<String> name_parts;

        public Predicate(List<String> tokens) {
            Objects.requireNonNull(tokens);
            if (!(0 < tokens.size() && tokens.size() <= 3)) {
                throw new IllegalArgumentException("Input should consists of 1,2 or 3 tokens!");
            }
            this.name_parts = tokens;
        }

        public int getLength() { return name_parts.size(); }
        /// @returns first part of the predicate. assumes this is a lemma (e.g. for `want.agent` returns `want`)
        public String getLemma() { return name_parts.get(0); }
        /// @returns predicate without first part (so without the lemma)
        public List<String> getDelexPred() { return name_parts.subList(1, getLength()); }
        public String getDelexPredAsString() { return String.join(".", getDelexPred()); }
        /// @returns all the parts of the predicate name
        public List<String> getNameParts() { return name_parts; }
        public String toString() {return String.join(" . ", name_parts); }
    }

    /**
     * Represents a term, consisting of a predicate name and a list of arguments
     *
     * e.g. <code>want.agent(x_2, Ava)</code> , <code>boy(x_1)</code> or <code>cookie.nmod.beside(x_4,x_7)</code>
     */
    public static class Term {
        private final Predicate predicate;
        private final List<Argument> arguments;

        public Term(List<String> predicate, List<Argument> arguments) {  // note: arguments assumed to be one Argument per index (so without x _ , )
            Objects.requireNonNull(predicate);
            Objects.requireNonNull(arguments);
            int argslength = arguments.size();
            if (!(0 < argslength && argslength <= 2)) {
                throw new IllegalArgumentException("Should have 1 or 2 arguments!");
            }
            this.predicate = new Predicate(predicate);
            this.arguments = arguments;
            // transform strings to Argument s and fill array with them
//            this.arguments = new Argument[argslength];
//            for (int i = 0; i < argslength; i++) {
//                String tok = arguments[i];
//                this.arguments[i] = new Argument(tok);
//            }
        }

        public Predicate getPredicate() { return predicate; }
        public List<Argument> getArguments() { return arguments; }
        public int getValency() { return arguments.size(); }
        public boolean hasTwoArguments() { return getValency()==2; }

        public String getLemma() { return predicate.getLemma();}
        /// returns first Argument (that is assumed to be the 'lemma'/'lexical' argument)
        public Argument getLemmaArgument() { return arguments.get(0); }
        /// the first argument of the predicate is assumed to be the index (0-based) for the sentence token (except for lambda var)
        public int getLemmaIndex() throws RuntimeException {  // throws exception if first argument not index (but lambda var)
            Argument a = getLemmaArgument();
            if (a.isIndex()) {return a.getIndex();}
            else {
                throw new RuntimeException("First argument is not an index, can't return meaningful number!");
            }
        }

        public String toString() {  // todo make more efficient?
            String[] args = new String[getValency()];
            for (int i = 0; i < getValency(); i++) {
                Argument a = arguments.get(i);
                args[i] = a.raw;
            }
            return predicate.toString()+"("+String.join(" , ", args)+")";
        }

        // todo test this class!!!!
        /**
         * Compares two terms for the purpose of sorting them (relevant for exact match for instance):
         * - term with smaller first argument index goes first
         * - if same first index, check whether second index is the same (only if both are indices)
         * - otherwise, rely on delexicalized predicate to sort terms
         * */
        public static class TermComparer implements Comparator<Term> {
            /* Train 100 examples
             * L18: lend . agent ( x _ 1 , Liam ) AND lend . theme ( x _ 1 , x _ 3 ) AND lend . recipient ( x _ 1 , Audrey )
             * --> sorting based on 'agent', 'theme', 'recipient' if first argument is equal?
             * L14: LAMBDA a . LAMBDA e . smile . agent ( e , a )
             * L5: LAMBDA a . LAMBDA b . LAMBDA e . pack . agent ( e , b ) AND pack . theme ( e , a )
             * --> sort b < a as second argument, or as above based on 'agent' vs 'theme' ?
             * L54: hope . agent ( x _ 1 , Emma ) AND hope . ccomp ( x _ 1 , x _ 5 )
             * L3: try . agent ( x _ 1 , Emma ) AND try . xcomp ( x _ 1 , x _ 3 )
             * L19: cloud ( x _ 3 ) AND cloud . nmod . in ( x _ 3 , x _ 6 )
             * --> lower valency term goes first?
             * conclusions: (not sure)
             * - first argument index: smaller goes first
             * - if same first argument: lower valency goes first / based on delexicalized predicate name (agent < theme...),
             */
            @Override
            public int compare(Term o1, Term o2) {
                Objects.requireNonNull(o1); Objects.requireNonNull(o2);
                // return -1 if o1 < o2, return 0 if o1 == o2, return +1 if o1 > o2
                /*
                (a)  sgn(compare(x, y)) == -sgn(compare(y, x)) for all x and y. (This implies that compare(x, y) must throw an exception if and only if compare(y, x) throws an exception.)
                (b) The implementor must also ensure that the relation is transitive: ((compare(x, y)>0) && (compare(y, z)>0)) implies compare(x, z)>0.
                (c) Finally, the implementor must ensure that compare(x, y)==0 implies that sgn(compare(x, z))==sgn(compare(y, z)) for all z.
                (d) not strictly required that (compare(x, y)==0) == (x.equals(y)).  any comparator that violates this condition should clearly indicate this fact. The recommended language is "Note: this comparator imposes orderings that are inconsistent with equals."
                */
                if (o1.equals(o2)) {return 0;}
                // todo comparison between lambda vars and indices is not defined?
                // (1) first argument index
                Argument firstArgO1 = o1.getLemmaArgument();
                Argument firstArgO2 = o2.getLemmaArgument();
                if (firstArgO1.isProperName() || firstArgO2.isProperName()) {
                    throw new IllegalArgumentException("Ill-formed logical form? First argument of term can't be proper name.");
                }
                if (firstArgO1.isIndex() != firstArgO2.isIndex()) {  // todo better check needed for 3 types!!! (inpput validation)
                    throw new ClassCastException("Can't compare first arguments of different type");
                }
                if (firstArgO1.isLambdaVar() && firstArgO2.isLambdaVar() && !firstArgO1.equals(firstArgO2)) {
                    throw new IllegalArgumentException("Undefined what to do with two terms whose first arguments are different lambda variables");
                }
                if (firstArgO1.isIndex() && firstArgO2.isIndex()) {  // compare two indices (first, then second if possible)
                    int o1idx = firstArgO1.getIndex();
                    int o2idx = firstArgO2.getIndex();
                    if (o1idx < o2idx) { return -1;}
                    if (o1idx > o2idx) { return 1;}
                    // if first arguments are equal indices and second arguments are indices too, compare them:
                    if (o1idx == o2idx && o1.hasTwoArguments() && o2.hasTwoArguments()) {
                        Argument sndArgO1 = o1.getArguments().get(1);
                        Argument sndArgO2 = o2.getArguments().get(1);
                        if (sndArgO1.isIndex() && sndArgO2.isIndex()) {
                            int cmp = Integer.compare(sndArgO1.getIndex(), sndArgO2.getIndex());
                            if (cmp != 0) return cmp;  // if not same second argument index, we can make a choice here
                        }
                    }
                }

                // (2) valency: lower valency first:   cloud(3) < cloud.nmod.in(3,1)
                if (o1.getValency() != o2.getValency()) { // for different valency: the lower one goes first
                    return o1.getValency() < o2.getValency()?-1:1;
                }
                // (3) second part of predicate names:  agent < theme ...
                assert(o1.getValency()==o2.getValency() && o1.getValency()==2);
                assert(o1.getLemma().equals(o2.getLemma()));
                String predO1 = o1.getPredicate().getDelexPredAsString();
                String predO2 = o2.getPredicate().getDelexPredAsString();
                assert(predO1 != null && predO2 != null);
                return predO1.compareTo(predO2);
            }
        } // comparator<term>
    }
}
