package regex.mutrex.parallel;

import dk.brics.automaton.RegExp;
import regex.distinguishing.DSgenPolicy;
import regex.distinguishing.DistStringCreator;
import regex.mutrex.ds.DSSet;
import regex.mutrex.ds.DSSetGenerator;
import regex.mutrex.ds.DistinguishingAutomaton;
import regex.mutrex.ds.RegexWAutomata;
import regex.operators.RegexMutator.MutatedRegExp;

import java.util.*;

public class DAsParallelCollectDSSetGenerator extends DSSetGenerator {
	public static DSSetGenerator generator = new DAsParallelCollectDSSetGenerator();

	@Override
	public void addStringsToDSSet(DSSet dsS, RegExp regex, Iterator<MutatedRegExp> mutants) {
		List<Boolean> trueFalse = Arrays.asList(true, false);
		MutantsManager mutantsManager = new MutantsManager(mutants);
		Set<DistinguishingAutomatonTh> datS = new HashSet<DistinguishingAutomatonTh>();
		RegexWAutomata regexWithAutomata = new RegexWAutomata(regex);
		while (!mutantsManager.stop) {
			// mutant not covered by the created distinguishing automata
			MutantForDasParallelCollector mutant = mutantsManager.getNotCoveredByCurrentDAs(datS);
			if (mutant != null) {
				assert mutant.isLocked();
				// randomly generate a positive or negative da
				DistinguishingAutomatonTh dat = null;
				Collections.shuffle(trueFalse);
				for (boolean b : trueFalse) {
					DistinguishingAutomaton newDa = new DistinguishingAutomaton(regexWithAutomata, b);
					if (newDa.add(mutant.description,mutant.getRegexWithAutomata())) {
						assert newDa.getMutants().size() == 1;
						assert DistStringCreator.getDS(regex, mutant.getRegex(), DSgenPolicy.RANDOM) != null;
						dat = new DistinguishingAutomatonTh(newDa, mutantsManager, dsS);
						datS.add(dat);
						mutant.setVisitedDA(dat);
						mutantsManager.coverMutant(mutant);
						mutant.unlock();
						dat.start();
						mutantsManager.mutantConsidered();
						break;
					}
				}
				// if no da has been created, it means that the mutant is
				// equivalent (tested both with positive and negative das)
				if (dat == null) {
					mutant.setTestedPositiveWithR();
					mutant.setTestedNegativeWithR();
					assert mutant.isEquivalent();
					mutant.unlock();
					mutantsManager.mutantConsidered();
					assert DistStringCreator.getDS(regex, mutant.getRegex(), DSgenPolicy.RANDOM) == null;
				}
			}
		}
		for (DistinguishingAutomatonTh dat : datS) {
			try {
				dat.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		//System.out.print(datS.size() + "\t");
		// assert mutantsManager.mutants.parallelStream().allMatch(m ->
		// (m.isCovered || m.isEquivalent()));
		if (this.getClass().desiredAssertionStatus()) {
			for (MutantForDasParallelCollector m : mutantsManager.mutants) {
				assert m.isCovered || m.isEquivalent();
			}
		}
	}
}

/*class RandomList<T> implements Iterable<T> {
	private List<T> elements;

	public RandomList() {
		elements = new ArrayList<T>();
	}

	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {
			// Java 8
			// List<Integer> indexes = IntStream.range(0, elements.size() -
			// 1).boxed().collect(Collectors.toList());
			List<Integer> indexes;
			{
				indexes = new ArrayList<Integer>();
				for (int i = 0; i < elements.size(); i++) {
					indexes.add(i);
				}
			}

			Random rnd = new Random();

			@Override
			public T next() {
				int i = rnd.nextInt(indexes.size());
				T element = elements.get(indexes.remove(i));
				return element;
			}

			@Override
			public boolean hasNext() {
				return indexes.size() > 0;
			}
		};
	}
}*/