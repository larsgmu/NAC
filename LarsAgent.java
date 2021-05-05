import java.util.List;


import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.bidding.BidDetails;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.BidHistory;
import genius.core.BidIterator;

/*
 * 
 * LarsAgent returns the offer that maximizes its own profit during the first half of the trading session.
 * In the second half until t=0.75, it utilizes 'Relative Tit for Tat'. And in the last 0.25 it offer a random offer. 
 * In this phase you only accept the offer that is on the table,
 * if the profit of the offer is greater than the last offer of the LarsAgent and greater than 0.8 in overall utility.
 * 
 * Generally the Agent accepts an offer if it's above "minOffer", and better than the next calculated bid.
 */

public class LarsAgent extends AbstractNegotiationParty {
    private final String description = "LarsAgent - Winner of ANAC 2040";

    // Bids
    private Bid lastReceivedOffer; 
    private Bid myLastOffer;
    private BidHistory receivedOffers;
    private double minOffer = 0.8;
    
    // Timings
    private double timeLimitMaxUtil = 0.5;
    private double timeLimitTFT = 0.75;
    private double timeLimitArbitrary = 0.98;
    
    
    // Tit For Tat variables
    private int delta = 3;		// For the relative Tit-For-Tat, how many steps/bids back to compare

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);
        receivedOffers = new BidHistory();
    
    }

    /**
     
     *
     * When this function is called, the Party is expected to choose one of the actions from the possible
     * list of actions and return an instance of the chosen action.
    
     * @param list
     * @return
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {
    	
        double time = getTimeLine().getTime();  

        if (time < timeLimitMaxUtil) {        	
        	Bid maxUtilBid = this.getMaxUtilityBid();
        	if(lastReceivedOffer != null && this.utilitySpace.getUtility(lastReceivedOffer) > this.utilitySpace.getUtility(maxUtilBid) && this.utilitySpace.getUtility(lastReceivedOffer)>minOffer)
        		return new Accept(this.getPartyId(), lastReceivedOffer);
        	myLastOffer = maxUtilBid;
            return new Offer(this.getPartyId(), maxUtilBid); 
            
        } 
        
        else if(time < timeLimitTFT) {
        	// Relative Tit-For-Tat   
        	// The agent reproduces, in percentage terms, the behavior that its opponent performed ∂ >= 1 steps ago.
        	Bid tft = relativeTitForTat();
        	if(getUtility(lastReceivedOffer)>this.utilitySpace.getUtility(tft) && this.utilitySpace.getUtility(lastReceivedOffer)>minOffer) {
        		return new Accept(this.getPartyId(),lastReceivedOffer);
        	}
        	myLastOffer = tft;
        	return new Offer(this.getPartyId(), tft);
        	
        	
        }
        
        else if(time < timeLimitArbitrary) {
        	myLastOffer = generateRandomAcceptableBid();
            if (lastReceivedOffer != null
                && myLastOffer != null
                && this.utilitySpace.getUtility(lastReceivedOffer) > this.utilitySpace.getUtility(myLastOffer) || this.utilitySpace.getUtility(lastReceivedOffer)>minOffer) {
                return new Accept(this.getPartyId(), lastReceivedOffer);
            } else {
                // Offering a random bid
                return new Offer(this.getPartyId(), myLastOffer);
            }
        }
        
        else {
			
        	// Time is up, accept if above 0.8
        	if (getUtility(lastReceivedOffer) > minOffer) {
				return new Accept(getPartyId(), lastReceivedOffer);
			}
        	return new Offer(getPartyId(),  myLastOffer=generateRandomAcceptableBid());
        }
    }
    
    /**
     * This method generates a random bid that gives the agent at least "minOffer" in utility.
     * @param 
     * 
     * @return Bid
     */
    private Bid generateRandomAcceptableBid(){
    	
    	Bid bid = generateRandomBid();
    	double util = this.utilitySpace.getUtility(bid);
    	while(util < minOffer) {
    		bid = generateRandomBid();
    		util = this.utilitySpace.getUtility(bid);
    	}
    	
    	return bid;
    }
    
    /**
     * This method returns a Bid based on the Tit for Tat strategy
     * @param 
     * 
     * @return Bid
     */
    private Bid relativeTitForTat() {
    	Bid bid;
    	
    	// Reproduce, in percentage terms, what the opponent offered delta steps ago.
    	// If my utility increased, then make a offer that makes their utility increase and vice versa
    	
    	double avgUtility = receivedOffers.getAverageUtility();
    	double utilityDeltaOffer = utility(delta); // Get utility of offer 'delta'-steps ago
    	double myLastUtility = getUtility(myLastOffer);
    	
    	
    	// Better than average? - Respond with increasing their utility(done by decreasing ours)
    	if(utilityDeltaOffer > avgUtility) {
    		bid = generateBidWithUtility(myLastUtility*0.95);
    	}
    	else if (utilityDeltaOffer<avgUtility){
    		bid = generateBidWithUtility(myLastUtility*1.05);
    	}
    	else {
    		bid = generateBidWithUtility(avgUtility);
    	}
    	
    	if(getUtility(bid)<minOffer)
    		return generateBidWithUtility(minOffer); 
    	
    	return bid;
    	
    }
    
    /**
     * This method returns a bid with a target utility
     * @param targetUtil
     * 
     * @return Bid
     */
    
    private Bid generateBidWithUtility(double targetUtil) {
    	BidIterator bidIterator = new BidIterator(this.getDomain()); // Create a iterator with all possible bid combinations
    	BidHistory bidHistory = new BidHistory();
    	
    	// Put all bids with correct utility in a history
    	while(bidIterator.hasNext()) {
    		Bid bid = bidIterator.next();
    		double util = getUtility(bid);
    		if(util >= targetUtil*0.95 && util <= targetUtil*1.05) { 
    			bidHistory.add(new BidDetails(bid,util));
    		}
    	}
    	
    	// Return bid in history with best utility
    	return bidHistory.getBestBidDetails().getBid();
    	
    }
    
    /**
     * This method returns utility of offer received 'steps' ago
     * @param steps
     * 
     * @return utility
     */
    
    private double utility(int steps) {
    	
    	return this.utilitySpace.getUtility(receivedOffers.getHistory().
    			get(receivedOffers.getHistory().size()-1-steps).getBid());
    }
    

    /**
     * Este método es llamado para informar a la parte que otra Parte de la Negociación eligió una Acción.
     * @param sender
     * @param act
     */
    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer) { // el remitente está haciendo una oferta
            Offer offer = (Offer) act;

         // Store last received offer and put it in the history
            lastReceivedOffer = offer.getBid();
            receivedOffers.add(new BidDetails(lastReceivedOffer, this.utilitySpace.getUtility(lastReceivedOffer), getTimeLine().getTime()));
        }
    }

    /**
     * Una descripción legible para este partido.
     * @return
     */
    @Override
    public String getDescription() {
        return description;
    }

    private Bid getMaxUtilityBid() {
        try {
            return this.utilitySpace.getMaxUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}