package smartRegex.evolutionEngine;

import com.mifmif.common.regex.Generex;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import smartRegex.MainClass;
import smartRegex.utils.FailureResidualIndex;
import smartRegex.utils.LabeledString;
import smartRegex.utils.RegexCandidate;

import java.util.*;

public abstract class EvolutionEngine {

    private String REGEX_ORACLE, REGEX_UNIVERSE, REGEX_START;
    private int MAX_INFINITE;
    private int N_POP;
    int MAX_ITERATIONS, N_PARENTS, N_STRINGS;
    float HOM_PERCENTAGE;
    boolean USE_HOM, SPECIALIZE;
    FailureResidualIndex fri;
    List<RegexCandidate> pop, parents, offspring;
    SplittableRandom rnd = new SplittableRandom();
    public static List<LabeledString> strings = new ArrayList<>();

    EvolutionEngine() {
        HOM_PERCENTAGE = MainClass.HOM_PERC;
        USE_HOM = MainClass.USE_HOM;
        N_POP = MainClass.N_POP;
        MAX_ITERATIONS = MainClass.N_ITER;
        N_PARENTS = MainClass.N_PARENTS;
        N_STRINGS = MainClass.N_STRINGS;
        REGEX_ORACLE = MainClass.REGEX_ORACLE;
        REGEX_UNIVERSE = MainClass.REGEX_UNIVERSE;
        REGEX_START = MainClass.REGEX_START;
        MAX_INFINITE = MainClass.MAX_INFINITE;
        SPECIALIZE = MainClass.SPECIALIZE;
        fri = new FailureResidualIndex(new RegExp(REGEX_ORACLE), new RegExp(REGEX_START));
        pop = new ArrayList<>();
        parents = new ArrayList<>();
        offspring = new ArrayList<>();
        if (MainClass.useFile)
            strings = MainClass.strings;
        else
            initializeTestStrings();
        initializePop();
    }

    public abstract double[] run();

    private void initializeTestStrings() {
        // Generating string from the given regexps
        RegExp rU = new RegExp(REGEX_UNIVERSE);
        RegExp rO = new RegExp(REGEX_ORACLE);
        Automaton aO = rO.toAutomaton();
        Automaton aU = rU.toAutomaton();
        Automaton aO_comp = aU.intersection(aO.complement());
        Generex generex = new Generex(aO);
        for (int i = 0; i < N_STRINGS/2; i++) {
            strings.add(new LabeledString(generex.random(), true));
        }
        generex = new Generex(aO_comp);
        for (int i = 0; i < N_STRINGS/2; i++) {
            strings.add(new LabeledString(generex.random(), false));
        }
    }

    private void initializePop() {
        for (int i = 0; i < N_POP; i++) {
            RegexCandidate r = new RegexCandidate(REGEX_START);
            pop.add(r);
        }
    }

    void selectParents() {
        // Each individual has a range proportional to the fit and is chosen as the parent if the random falls in its range.
        // The number of parents is fixed, each individual can only be chosen once
        double sum = 0;
        for (RegexCandidate r: pop) {
            sum += r.fitness;
        }
        if (sum <= 0) {
            throw new RuntimeException("The sum of all fitness is <= 0!!!");
        }
        double[] ranges = new double[pop.size()];
        double lastFit = 0;
        for (int i = 0; i < pop.size(); i++) {
            ranges[i] = lastFit + pop.get(i).fitness / sum;
            lastFit = ranges[i];
        }
        HashSet<Integer> chosen = new HashSet<>();
        for (int i = 0; i < N_PARENTS; i++) {
            int index = 0;
            do {
                double t = rnd.nextDouble();
                for (int j = 0; j < N_POP; j++) {
                    if (t < ranges[j]) {
                        index = j;
                        break;
                    }
                }
            } while (chosen.contains(index));
            chosen.add(index);
            parents.add(pop.get(index));
        }
    }

    void replaceWorst() {
        //increasing order, first those to be replaced
        pop.sort(Comparator.comparingDouble(r3 -> r3.fitness));
        //descending order, first the best
        offspring.sort((r1, r2) -> Double.compare(r2.fitness, r1.fitness));
        int nSubs = 0;
        for (RegexCandidate starter: pop) {
            RegexCandidate sub = offspring.get(nSubs);
            if (sub.fitness > starter.fitness) {
                starter.replace(sub);
                if (++nSubs >= offspring.size()) {
                    break;
                }
            } else {
                break;
            }
        }
        for (RegexCandidate r : pop){
            r.regex = new RegExp(r.regex.toString()
                    .replace("*", "+")
                    .replace("?", "+")
                    .replace("{1,}", "{1," + (2+rnd.nextInt(MAX_INFINITE -1)) + "}"));
        }
    }

    void specializeFinalRegex(){
        // If the regex contains {n, n} all the combinations are tested and the best one is chosen
        pop.sort(Comparator.comparingDouble(r3 -> r3.fitness));
        String best = pop.get(pop.size() - 1).regex.toString();
        if (!best.contains("{")) {
            throw new RuntimeException("Cannot specialize regex: no variable occurrences found!");
        }
        // Getting all the n in the regex
        String[] pz = best.replace("{", "£").replace("}", "£").replace(",", "£").split("£");
        ArrayList<Integer> occurrences = new ArrayList<>();
        for (String s: pz) {
            if (s.length() == 1) {
                try {
                    occurrences.add(Integer.parseInt(s));
                } catch (NumberFormatException ignored){}
            }
        }
        int nClass = occurrences.size() / 2;
        StringBuilder sb = new StringBuilder();
        String[] classes = new String[nClass];
        for (int i = 0; i < nClass; i++) {
            int nStart = occurrences.get(i*2);
            int nEnd = occurrences.get(i*2+1);
            classes[i] = "\\{" + nStart + "," + nEnd + "}";
            sb.append("[").append(nStart).append("-").append(nEnd).append("]");
        }
        // Generating all the possible combinations
        Generex gen = new Generex(sb.toString());
        List<String> combinations = gen.getAllMatchedStrings();
        ArrayList<RegexCandidate> regexps = new ArrayList<>();
        for (String s: combinations) {
            RegexCandidate r = new RegexCandidate(replaceBraces(s, best, classes));
            regexps.add(r);
        }
        regexps.sort(Comparator.comparingDouble(r -> r.fitness));
        fri.computeRatio(regexps.get(regexps.size()-1).regex);
        MainClass.finalRegex = regexps.get(regexps.size()-1);
        MainClass.finalFri = fri.numFinalFaults;
    }

    private String replaceBraces(String replacing, String regex, String[] replaced) {
        // Replacing {n, n} with all possible combinations
        for (int i = 0; i < replaced.length; i++) {
            regex = regex.replaceFirst(replaced[i], "{" + replacing.charAt(i) + "}");
        }
        return regex;
    }
}
