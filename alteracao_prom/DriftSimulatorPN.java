package org.processmining.petrinetsimulator.algorithms.driftsimulator.petrinet;


import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.petrinetsimulator.algorithms.driftsimulator.abstr.DriftSimulatorAbstr;
import org.processmining.petrinetsimulator.algorithms.tracesimulator.abstr.TraceSimulator;
import org.processmining.petrinetsimulator.algorithms.tracesimulator.petrinet.TraceSimulatorPN;
import org.processmining.petrinetsimulator.constants.SettingsConstants;
import org.processmining.petrinetsimulator.parameters.ConceptDriftSettings;
import org.processmining.petrinetsimulator.utils.StatisticsUtils;

import cern.jet.random.Uniform;
import cern.jet.random.engine.DRand;

public class DriftSimulatorPN extends DriftSimulatorAbstr<Petrinet> {

	public DriftSimulatorPN(PluginContext context, XFactory factory, ConceptDriftSettings settings) {
		super(context, factory, settings);
	}

	public XLog simulateDrift(Petrinet model1, Marking init1, Petrinet model2, Marking init2) {

		TraceSimulator baseSim = new TraceSimulatorPN(context, model1, init1, factory,
				settings.getCoreSimulationSettings());
		TraceSimulator driftSim = new TraceSimulatorPN(context, model2, init2, factory,
				settings.getCoreSimulationSettings());

		//for every remaining drift point
		boolean isOdd = false;

		int traceID = 0;
		long currentTime = settings.getCoreSimulationSettings().getStartDate().getTime();

		DRand rand = new DRand(new Date(System.currentTimeMillis()));
		Uniform numberGenerator = new Uniform(rand);

		XLog log = factory.createLog();

		List<Integer> baseLog = new ArrayList<>();
		List<Integer> driftLog = new ArrayList<>();
		List<Integer> allDrifts = new ArrayList<>();
		List<List<Integer>> driftsList = new ArrayList<>();

		for (int i = 0; i <= settings.getNumberOfDrifts(); i++) {

			//simulate the stable time
			long stableFinalTime = currentTime + Math.round(settings.getDurationOfStablePeriod().nextDouble());

			while (currentTime < stableFinalTime) {
				if (!isOdd) {
					if (settings.getSamplingProbEvenPeriods() > numberGenerator.nextDouble()) {
						// System.out.println("========== BASE ==========");
						log.add(baseSim.simulateTrace(currentTime, traceID));
						// System.out.println("BASE === [" + traceID + "]");
						baseLog.add(traceID);
					}
					else {
						// System.out.println("========== DRIFT ==========");
						log.add(driftSim.simulateTrace(currentTime, traceID));
						// System.out.println("DRIFT === [" + traceID + "]");
						driftLog.add(traceID);
					}

					timePoints.add(new ImmutablePair<Long, Double>(currentTime, settings.getSamplingProbEvenPeriods()));
					tracePoints.add(
							new ImmutablePair<Long, Double>((long) traceID, settings.getSamplingProbEvenPeriods()));
				} else {
					if (settings.getSamplingProbOddPeriods() > numberGenerator.nextDouble()) {
						// System.out.println("========== BASE ==========");
						log.add(baseSim.simulateTrace(currentTime, traceID));
						// System.out.println("BASE === [" + traceID + "]");
						baseLog.add(traceID);
					}

					else {
						// System.out.println("========== DRIFT ==========");
						log.add(driftSim.simulateTrace(currentTime, traceID));
						// System.out.println(" DRIFT === [" + traceID + "]");
						driftLog.add(traceID);
					}

					timePoints.add(new ImmutablePair<Long, Double>(currentTime, settings.getSamplingProbOddPeriods()));
					tracePoints
							.add(new ImmutablePair<Long, Double>((long) traceID, settings.getSamplingProbOddPeriods()));
				}

				traceID++;
				currentTime = currentTime
						+ Math.round(settings.getCoreSimulationSettings().getCaseArrivalDistribution().nextDouble());
			}
			allDrifts.add(traceID);

			// simulate unstable (drifting) time
			if (i != settings.getNumberOfDrifts()) {
				//do not add drift, end with a stable period.
				driftPoints.add(new Date(currentTime));//first trace with drift
				// System.out.println("=============");
				if (!settings.getDriftType().equals(SettingsConstants.SUDDEN)) {

					long driftFinalTime = stableFinalTime
							+ Math.round(settings.getDurationOfDriftPeriod().nextDouble());
					long driftStartTime = stableFinalTime;

					if (settings.getDriftType().equals(SettingsConstants.MOMENTARY)) {
						//drift period is split in two, ascendant (more) and descendant (less). we assume equal splits.

						while (currentTime < driftFinalTime) {
							long midTime = driftStartTime + (driftFinalTime - driftStartTime) / 2;
							if (currentTime < midTime) {
								//ascendant function
								double newnumber = StatisticsUtils.getProbability(currentTime - driftStartTime,
										midTime - driftStartTime, settings.getSamplingProbEvenPeriods(),
										settings.getSamplingProbOddPeriods(), settings.getDurationOfDriftPeriod(),
										settings.getDriftTransitionFunction());
								if (newnumber > numberGenerator.nextDouble())
									log.add(baseSim.simulateTrace(currentTime, traceID));
								else
									log.add(driftSim.simulateTrace(currentTime, traceID));
								timePoints.add(new ImmutablePair<Long, Double>(currentTime, newnumber));
								tracePoints.add(new ImmutablePair<Long, Double>((long) traceID, newnumber));
							} else {
								//descendant function
								double newnumber = StatisticsUtils.getProbability(currentTime - midTime,
										driftFinalTime - midTime, settings.getSamplingProbOddPeriods(),
										settings.getSamplingProbEvenPeriods(), settings.getDurationOfDriftPeriod(),
										settings.getDriftTransitionFunction());
								if (newnumber > numberGenerator.nextDouble())
									log.add(baseSim.simulateTrace(currentTime, traceID));
								else
									log.add(driftSim.simulateTrace(currentTime, traceID));
								timePoints.add(new ImmutablePair<Long, Double>(currentTime, newnumber));
								tracePoints.add(new ImmutablePair<Long, Double>((long) traceID, newnumber));
							}
							// System.out.println("=======================================================" + log);
							traceID++;
							currentTime = currentTime + Math.round(
									settings.getCoreSimulationSettings().getCaseArrivalDistribution().nextDouble());

						}
						driftPoints.add(new Date(currentTime));//first trace without drift

					} else if (settings.getDriftType().equals(SettingsConstants.GRADUAL)) {

						while (currentTime < driftFinalTime) {

							if (!isOdd) {
								double newnumber = StatisticsUtils.getProbability(currentTime - driftStartTime,
										driftFinalTime - driftStartTime, settings.getSamplingProbEvenPeriods(),
										settings.getSamplingProbOddPeriods(), settings.getDurationOfDriftPeriod(),
										settings.getDriftTransitionFunction());
								if (newnumber > numberGenerator.nextDouble())
									log.add(baseSim.simulateTrace(currentTime, traceID));
								else
									log.add(driftSim.simulateTrace(currentTime, traceID));
								timePoints.add(new ImmutablePair<Long, Double>(currentTime, newnumber));
								tracePoints.add(new ImmutablePair<Long, Double>((long) traceID, newnumber));

							} else {
								double newnumber = StatisticsUtils.getProbability(currentTime - driftStartTime,
										driftFinalTime - driftStartTime, settings.getSamplingProbOddPeriods(),
										settings.getSamplingProbEvenPeriods(), settings.getDurationOfDriftPeriod(),
										settings.getDriftTransitionFunction());
								if (newnumber > numberGenerator.nextDouble())
									log.add(baseSim.simulateTrace(currentTime, traceID));
								else
									log.add(driftSim.simulateTrace(currentTime, traceID));
								timePoints.add(new ImmutablePair<Long, Double>(currentTime, newnumber));
								tracePoints.add(new ImmutablePair<Long, Double>((long) traceID, newnumber));
							}

							traceID++;
							currentTime = currentTime + Math.round(
									settings.getCoreSimulationSettings().getCaseArrivalDistribution().nextDouble());
						}
						driftPoints.add(new Date(currentTime));//first trace without drift
					}
				}
			}

			//from even to odd and viceversa, except in momentary drift where the even state happens always
			if (!settings.getDriftType().equals(SettingsConstants.MOMENTARY))
				isOdd = !isOdd;
		}

		// Remove ultimo elemento traceID
		allDrifts.remove(allDrifts.size() - 1);
		driftsList.add(allDrifts);
		driftsList.add(baseLog);
		driftsList.add(driftLog);
		// Printa no console as listas do drifts detectados, lista da baseLog, lista da driftLog e trace points utilizados para plotagem
		System.out.println("========================================================================");
		System.out.println("== | DETECTED DRIFTS | =================================================");
		System.out.println(allDrifts);
		System.out.println(" ");
		System.out.println("== | BASE LOG | ========================================================");
		System.out.println(baseLog);
		System.out.println(" ");
		System.out.println("== | DRIFT LOG | =======================================================");
		System.out.println(driftLog);
		System.out.println(" ");
		System.out.println("== | TRACE POINTS | ====================================================");
		System.out.println(tracePoints);
		System.out.println("========================================================================");

		// Gerar arquivo .txt com as listas allDrifts, baseLog e driftLog
		try {
			FileWriter writer = new FileWriter("driftsDetectados.txt");
			writer.write("Detected drifts: " + allDrifts + "\nBaseLog: " + baseLog +
					"\nDriftLog: " + driftLog);
			writer.close();
			System.out.println("Salvei!");

		} catch (IOException exc) {
			System.out.println("Erro ao gravar!");
		}

		XAttributeMap driftsListMap = new XAttributeMapImpl();
		XAttribute allDriftsToXlog = new XAttributeLiteralImpl("drifts:", driftsList.toString());
		driftsListMap.put("new attribute", allDriftsToXlog);
		log.setAttributes(driftsListMap);

		return log;
	}


	public XLog simulateDrift(Petrinet model1, Petrinet model2) {
		// TODO Auto-generated method stub
		return null;
	}

}

