import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.BOAparameter;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.Issue;
import genius.core.issue.Value;
import genius.core.utility.AdditiveUtilitySpace;
import negotiator.boaframework.opponentmodel.tools.UtilitySpaceAdapter;

public class LarsModel extends OpponentModel {
	
	private Bid lastReceivedOffer;
    private BidHistory receivedOffers;
	private int numReceivedOffers;
	private double delta; 			// Constant for weight increment of the opponent model
	private Map<Integer, Double> estimatedWeights;
	private Map<Integer, HashMap<Value, Integer>> numReceivedValueOfAttribute; // Holds the number of times a issue has received offer with given value
	int cnt = 0;

	@Override
	public String getName() {
		return "Lars' Opponent Model";
	}
	
	@Override
	public void init(NegotiationSession negotiationSession, Map<String, Double> parameters) {
		super.init(negotiationSession, parameters);
		
		
		lastReceivedOffer     = null;
        receivedOffers 		  = new BidHistory();
		numReceivedOffers     = 0;
		delta 			      = 0.3;
		estimatedWeights      = new HashMap<>();
		numReceivedValueOfAttribute = new HashMap<>();
		List<Issue> issues = negotiationSession.getIssues(); // List with all possible attributes/issues
		
		// Iterate through all issues in session and set their weight to 0 and count to 0 
		for (Issue issue : issues) {
			estimatedWeights.put(issue.getNumber(), 0.0);
			numReceivedValueOfAttribute.put(issue.getNumber(), new HashMap<Value, Integer>());
		}
		
		
	}
	
	@Override
	protected void updateModel(Bid bid, double time) {
		
		// Extract Values of new received offer
		HashMap<Integer, Value> newReceivedValues  = bid.getValues();
		// Increment the counter received offers
		numReceivedOffers++;
		
		// If offer is the absolute first (only runs once)
		if (2 > numReceivedOffers ) {
			
			// Since first we don't need to update the weights
			// Have to set up the counter for the given Value
			lastReceivedOffer = bid;
			
			// Iterate through the IssueNumbers and set their count to 1
			for(Map.Entry<Integer, Value> valueEntry : newReceivedValues.entrySet()) {
					HashMap<Value, Integer> initCounterMap = new HashMap<>();
					initCounterMap.put(valueEntry.getValue(), 1);
					numReceivedValueOfAttribute.put(valueEntry.getKey(), initCounterMap);
				}
			return;
		}
		
		// Add bid to history
        receivedOffers.add(new BidDetails(lastReceivedOffer, getBidEvaluation(lastReceivedOffer), time));
		
		// Values of previous received bid
		HashMap<Integer, Value> lastReceivedValues = lastReceivedOffer.getValues();
		
		// Not first received bid anymore, thus iterate through all entries in bid:
		for(Map.Entry<Integer, Value> valueEntry : newReceivedValues.entrySet()) {
			
			Integer id    = valueEntry.getKey();
			Value 	value = valueEntry.getValue();
			
			// Check if value exists in counter from before
			if(numReceivedValueOfAttribute.get(id).containsKey(value)) {
				
				// Increment counter
				numReceivedValueOfAttribute.get(id).put(value, 
						numReceivedValueOfAttribute.get(id).get(value)+1);
			
				// If attribute value is same as previous offer, update the weights
				if(lastReceivedValues.get(id) == value) {
					estimatedWeights.put(id, estimatedWeights.get(id) + delta);
				}
			
			} else { // Haven't received this attribute value before
				
				// Add it to the counter for the first time
				numReceivedValueOfAttribute.get(id).put(value, 1);
			}
		}
		
		// Update last received offer
		lastReceivedOffer = bid;
	}
	
	@Override
	public double getBidEvaluation(Bid bid) {
		
		// All values of received bid
		HashMap<Integer, Value> newReceivedValues  = bid.getValues();
		double estimatedUtility = 0.0;
		
		// If first received offer return 0
		if(numReceivedOffers < 1) 
			return estimatedUtility;


		// Calculate the total sum of the weights w_i
		double sumOfEstimatedWeights = 0.0;
		for (HashMap.Entry<Integer, Double> weightEntry : estimatedWeights.entrySet()) {
			sumOfEstimatedWeights += weightEntry.getValue();
		}
		
		// For received offer see how many times we have received given attributes
		for (HashMap.Entry<Integer, Value> issueEntry : newReceivedValues.entrySet()) {
			
			Integer id  = issueEntry.getKey();
			Value value = issueEntry.getValue();
			
			// If new attribute value appears for first time we add it to counter:
			if(!numReceivedValueOfAttribute.get(id).containsKey(value)) {
				numReceivedValueOfAttribute.get(id).put(value, 1);
			}
			
			
			//Estimate V_i for this issue
			double estimated_issue_value = (double) (numReceivedValueOfAttribute.get(id).get(value))/((double)(numReceivedOffers));
			double normalized_issue_weight = estimatedWeights.get(id)/sumOfEstimatedWeights;
			
			estimatedUtility += normalized_issue_weight*estimated_issue_value;
		}
		double builtin_est_utility = opponentUtilitySpace.getUtility(bid);
		
		System.out.format("%d EST UTIL Freq model: %f\n", cnt, estimatedUtility);
		System.out.format("%d EST UTIL builtin model: %f\n",cnt, opponentUtilitySpace.getUtility(bid));
		System.out.format("%d EST UTIL builtin-freq/2 model: %f\n", cnt, estimatedUtility+(builtin_est_utility-estimatedUtility)/2);
		
		// Checks if the difference between the built in estimated utility is close to the frequency estimated.
		if(Math.abs(builtin_est_utility-estimatedUtility) < 0.2) {
			if(builtin_est_utility>estimatedUtility) {
				return estimatedUtility+(builtin_est_utility-estimatedUtility)/2;
			}else {
				return builtin_est_utility+(estimatedUtility-builtin_est_utility)/2;
			}
		}
		
		return estimatedUtility;
	}
	
	@Override
	public AdditiveUtilitySpace getOpponentUtilitySpace() {
		AdditiveUtilitySpace utilitySpace = new UtilitySpaceAdapter(this, this.negotiationSession.getDomain());
		return utilitySpace;
	}
	
	@Override
	public Set<BOAparameter> getParameterSpec(){
		Set<BOAparameter> set = new HashSet<BOAparameter>();
		/* Aqu� describe los par�metros que necesita el algoritmo de aprendizaje. Ejemplos:
			set.add(new BOAparameter("n", 20.0, "The number of own best offers to be used for genetic operations"));
			set.add(new BOAparameter("n_opponent", 20.0, "The number of opponent's best offers to be used for genetic operations"));
		*/
		return set;
	}

}
