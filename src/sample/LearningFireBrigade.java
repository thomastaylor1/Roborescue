package sample;

import static rescuecore2.misc.Handy.objectsToIDs;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;

import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.Property;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.messages.Command;
import rescuecore2.log.Logger;

import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.StandardPropertyURN;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.FireBrigade;

/**
   A sample fire brigade agent.
 */
public class LearningFireBrigade extends AbstractSampleAgent<FireBrigade> {
    private static final String MAX_WATER_KEY = "fire.tank.maximum";
    private static final String MAX_DISTANCE_KEY = "fire.extinguish.max-distance";
    private static final String MAX_POWER_KEY = "fire.extinguish.max-sum";
    
    private static int nbStates = 4;
    private static int nbActions = 3;
    
    private int state = 2;
    private int previous_state = 2;
    private int choice = 0;
    
    private final double alpha = 0.4;
    private final double beta = 8;
    private final double gamma = 0.9;
    private double reward;
    private double[][] Q; 
    
    public enum Action{
    	RANDOM_WALK(0),
    	REPLENISH(1),
    	EXTINGUISH(2);
    	
    	private final int value;
    	
    	Action(int value){
    		this.value = value;
    	}
    	
	   public static Action getAction(int value) {
	      for (Action a : Action.values()) {
	          if (a.value == value) return a;
	      }
	      return null;
	   }
	   
	   public static int getRandomAction() {
		   Random r = new Random();
		   return r.nextInt(Action.values().length);
	   }
    }
    
    /* 
     * actions : 
     *  0 	Random Walk
     *  1	Replenish
     *  2	Extinguish 	
     * etats : 
     * water? 0 ou 1
     * bat en feu? 0 ou 1
     * 0 = 0, 0
     * 1 = 0, 1
     * 2 = 1, 0
     * 3 = 1, 1
     * 
     * recompense : 
     * rew = -1 si l'action n'a pas marché
     * rew = 1 si un feu est éteint
     */
    
    private int maxWater;
    private int maxDistance;
    private int maxPower;
	
    
    public boolean haveWater() {
    	return me().getWater() > 0 && me().isWaterDefined();
    }
    
    public boolean fireInSight(ChangeSet changed) {
    	for (EntityID entity : changed.getChangedEntities()) {
    		StandardEntityURN entityType = StandardEntityURN.fromString(changed.getEntityURN(entity));
    		Set<Property> props = changed.getChangedProperties(entity);
//    		System.out.println(entityType);
    		//if (entityType.equals("urn:rescuecore2.standard:entity:building")) {
    		if(entityType.equals(StandardEntityURN.BUILDING)) {
    			for (Property prop : props) {
    				StandardPropertyURN propType = StandardPropertyURN.fromString(prop.getURN());
    				if (propType.equals(StandardPropertyURN.FIERYNESS)) {
    					int fieryness = (int) prop.getValue();
    					if (fieryness > 0) {
    						return true;
    					}
    				}
    			}
    		}
    	}
    	return false;
    }
    
    public List<EntityID> buildingsInMyRange(ChangeSet changed) {
    	List<EntityID> result = new ArrayList<EntityID>();
    	for (EntityID entity : changed.getChangedEntities()) {	
    		StandardEntityURN entityType = StandardEntityURN.fromString(changed.getEntityURN(entity));
    		if (entityType.equals(StandardEntityURN.BUILDING)){
    			result.add(entity);
    		}
    	}
        return result;
    }
    
    // ACTION extinguishFire
    public boolean extinguishFire(int time, EntityID id, ChangeSet changed) {
    	StandardEntityURN entityType = StandardEntityURN.fromString(changed.getEntityURN(id));
    	if (!entityType.equals(StandardEntityURN.BUILDING)) {
    		return false;
    	}
    	if (!haveWater()) {
    		System.out.println("can't extinguish : i dont have water");
    		return false;
    	}
    	if (model.getDistance(getID(), id) <= maxDistance) {
    		Logger.info("Extinguishing " + id);
            sendExtinguish(time, id, maxPower);
            sendSpeak(time, 1, ("Extinguishing " + id).getBytes());
            return true;
    	} else {
    		return false;
    	}
    }
    
    /* ACTION randomMove
     * @return l'action a marché
     */
    public boolean randomMove(int time) {
    	List<EntityID> path = randomWalk();
        Logger.info("Moving randomly");
        sendMove(time, path);
        return true;
    }
    
    //ACTION replenishWater unused
    public boolean replenishWaterOld(int time, ChangeSet changed) {
    	if (location() instanceof Refuge) {
            Logger.info("Filling with water at " + location());
            sendRest(time);
            return true;
    	} else {
    		return false;
    	}
    }
    
    /* ACTION plan a path to refuge + replenishWater
     * @return l'action a marche
     */
    public boolean replenishWater(int time, ChangeSet changed) {
    	List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
            Logger.info("Moving to refuge");
            sendMove(time, path);
        }
    	if (haveWater()) {
    		if (location() instanceof Refuge) {
				Logger.info("Filling with water at " + location());
	            sendRest(time);
    		}
    		return true;
    	} else {
    		return false;
    	}
    }
    
    /* Modification de l'état  */
    public int changeState(ChangeSet changed) {
    	int state = -1;
    	boolean isWater = haveWater();
    	boolean isFire = fireInSight(changed);
    	if (isWater == false && isFire == false) {
    		return 0;
    	}
    	if (isWater == false && isFire == true) {
    		return 1;
    	}
    	if (isWater == true && isFire == false) {
    		return 2;
    	}
    	if (isWater == true && isFire == true) {
    		return 3;
    	}
    	return state;
    }
    public int chooseAction() {
    	// Choix de l'action avec les valeurs courantes
    	return tirageDistribution();
    }
    
    public void majQ(int state, int previous_state, int choice) {
    	//Mise à jour de Q
    	double Qmax_row = maxTab(Q[choice]);
    	double delta = reward + Qmax_row - Q[previous_state][choice]; 
    	Q[previous_state][choice] += alpha *  delta;
    	reward = 0;
    }
    
    /* Calcule la probabilité de chaque action avec softmax sur les Q valeurs
     * puis effectue un tirage sur la distribution
     */
    public int tirageDistribution() {
    	// super mal codé, plein de choses inutiles
    	List<Double> probas = new ArrayList<>();
    	List<Double> probasCumulees = new ArrayList<>();
    	Random r = new Random();
    	double denom = 0;
    	for(int i=0;i<nbActions;i++) {
    		for(Double d: toList(Q[state])) {
        		denom += Math.exp(beta*d);
        	}
    		probas.add(Math.exp(beta*Q[state][i]) / denom);
    	}
    	double sum = 0;
    	for (Double proba : probas) {
    		sum += proba;
    		probasCumulees.add(sum);
    	}
    	probasCumulees.sort(Comparator.comparingDouble(Double::doubleValue)); //inutile
    	double tirage = r.nextDouble();
    	int i = 0;
    	for(Double proba : probasCumulees) {
    		i++;
    		if(proba.doubleValue() > tirage) {
    			return i - 1;
    		}
    	}
    	return i - 1;
    }
    
    public List<Double> toList(double[] array){
    	List<Double> l = new ArrayList<>();
    	for(int i=0; i < array.length; i++) {
    		l.add(array[i]);
    	}
    	return l;
    }
    
    public double maxTab(double[] t) {
    	List<Double> l = new ArrayList<>();
    	for(double d: t) {
    		l.add(d);
    	}
    	Collections.sort(l, (a,b)->
    		a.doubleValue()<b.doubleValue()?1:-1
    	);
    	return l.get(0);
    }
    
    @Override
    public String toString() {
        return "Learning fire brigade";
    }

    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.BUILDING, StandardEntityURN.REFUGE,StandardEntityURN.HYDRANT,StandardEntityURN.GAS_STATION);
        maxWater = config.getIntValue(MAX_WATER_KEY);
        maxDistance = config.getIntValue(MAX_DISTANCE_KEY);
        maxPower = config.getIntValue(MAX_POWER_KEY);
        Q = new double[nbStates][nbActions];
        Logger.info("Sample fire brigade connected: max extinguish distance = " + maxDistance + ", max power = " + maxPower + ", max tank = " + maxWater);
    }

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) {
        if (time == config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
            // Subscribe to channel 1
            sendSubscribe(time, 1);
        }
        for (Command next : heard) {
            Logger.debug("Heard " + next);
        }

        FireBrigade me = me();
		previous_state = state;
		state = changeState(changed);
        int old_choice = choice;
        choice = chooseAction();
        System.out.println("Etat courant : "+state);
        System.out.println("Choix de l'action : "+Action.getAction(choice));
        System.out.println("Water level : "+me().getWater());
		switch(choice) {
			case 0:
				randomMove(time);
		        break;	
	        case 1:
	        	if(replenishWater(time, changed)) {
	        		reward = 1.0;
	        	} else {
	        		reward = -0.1;
	        	}
	        	break;
	        case 2:
	        	if(buildingsInMyRange(changed).size() > 0) {
	        		EntityID id = buildingsInMyRange(changed).get(0);
	        		if (extinguishFire(time, id, changed)) {
		        		reward = 1.0;
		        	} else {
		        		//System.out.println("did not extinguish, recompense negative");
		        		reward = -1.0;
		        	}
	        	}
	        	break;
		}
		/* Mise à jour de la Q table */
		majQ(state, previous_state, old_choice);
        /* Affichage de la Q table */
        for (int i=0; i<nbStates; i++) {
        	for(int j=0; j<nbActions;j++) {
        		System.out.print(Q[i][j]+"\t");
        	}
        	System.out.println("State "+i);
        }
        System.out.println("~~~~~~~~~~~~");
    }

    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.FIRE_BRIGADE);
    }

    private Collection<EntityID> getBurningBuildings() {
        Collection<StandardEntity> e = model.getEntitiesOfType(StandardEntityURN.BUILDING);
        List<Building> result = new ArrayList<Building>();
        for (StandardEntity next : e) {
            if (next instanceof Building) {
                Building b = (Building)next;
                if (b.isOnFire()) {
                    result.add(b);
                }
            }
        }
        // Sort by distance
        Collections.sort(result, new DistanceSorter(location(), model));
        return objectsToIDs(result);
    }

    private List<EntityID> planPathToFire(EntityID target) {
        // Try to get to anything within maxDistance of the target
        Collection<StandardEntity> targets = model.getObjectsInRange(target, maxDistance);
        if (targets.isEmpty()) {
            return null;
        }
        return search.breadthFirstSearch(me().getPosition(), objectsToIDs(targets));
    }
}
