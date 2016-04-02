/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 * 
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.schedulers;

import java.util.ArrayList;
import java.util.List;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.ResCloudlet;

import org.cloudbus.cloudsim.core.CloudSim;

/**
 * CloudletSchedulerTimeShared implements a policy of scheduling performed by a
 * virtual machine to run its {@link Cloudlet Cloudlets}. Cloudlets execute in
 * time-shared manner in VM, i.e., it performs preemptive execution
 * of Cloudlets in the VM's PEs. Each VM has to have its own instance of a
 * CloudletScheduler.
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class CloudletSchedulerTimeShared extends CloudletSchedulerAbstract {

    /**
     * The number of PEs currently available for the VM using the scheduler,
     * according to the mips share provided to it by
     * {@link #updateVmProcessing(double, java.util.List)} method.
     */
    protected long currentCPUs;

    /**
     * Creates a new CloudletSchedulerTimeShared object. This method must be
     * invoked before starting the actual simulation.
     *
     * @pre $none
     * @post $none
     */
    public CloudletSchedulerTimeShared() {
        super();
        currentCPUs = 0;
    }

    @Override
    public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
        setCurrentMipsShare(mipsShare);

        if (getCloudletExecList().isEmpty()) {
            setPreviousTime(currentTime);
            return 0.0;
        }

        updateCloudletsProcessing(currentTime, mipsShare);
        removeFinishedCloudletsFromExecutionList();
        double nextEvent = 
                getEstimatedFinishTimeOfSoonerFinishingCloudlet(currentTime, mipsShare);

        setPreviousTime(currentTime);
        return nextEvent;
    }

    /**
     * Gets the estimated finish time of the cloudlet that is expected to 
     * finish executing sooner than all other ones that are executing
     * on the VM using this scheduler.
     * 
     * @param currentTime
     * @param mipsShare
     * @return 
     */
    private double getEstimatedFinishTimeOfSoonerFinishingCloudlet(double currentTime, List<Double> mipsShare) {
        double nextEvent = Double.MAX_VALUE;
        for (ResCloudlet rcl : getCloudletExecList()) {
            double estimatedFinishTime = 
                    getEstimatedFinishTimeOfCloudlet(rcl, currentTime, mipsShare);

            if (estimatedFinishTime < nextEvent) {
                nextEvent = estimatedFinishTime;
            }
        }
        return nextEvent;
    }

    /**
     * Gets the estimated time when a given cloudlet is supposed to finish executing.
     * @param rcl
     * @param currentTime
     * @param mipsShare
     * @return 
     */
    private double getEstimatedFinishTimeOfCloudlet(ResCloudlet rcl, double currentTime, List<Double> mipsShare) {
        double estimatedFinishTime = currentTime
                + (rcl.getRemainingCloudletLength() / 
                (getCapacity(mipsShare) * rcl.getNumberOfPes()));
        if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
            estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
        }
        return estimatedFinishTime;
    }

    /**
     * Removes finished cloudlets from the 
     * {@link #getCloudletExecList() list of cloudlets to execute}.
     */
    private void removeFinishedCloudletsFromExecutionList() {
        List<ResCloudlet> toRemove = new ArrayList<>();
        for (ResCloudlet rcl : getCloudletExecList()) {
            if (rcl.isFinished()) {
                toRemove.add(rcl);
                cloudletFinish(rcl);
            }
        }
        getCloudletExecList().removeAll(toRemove);
    }

    /**
     * Updates the processing of all cloudlets of the Vm using this scheduler.
     * @param currentTime current simulation time
     * @param mipsShare list with MIPS share of each Pe available to the scheduler
     */
    private void updateCloudletsProcessing(double currentTime, List<Double> mipsShare) {
        for (ResCloudlet rcl : getCloudletExecList()) {            
            long miLength = computeCloudletExecutionLengthForElapsedTime(rcl, currentTime, mipsShare);
            rcl.updateCloudletFinishedSoFar(miLength);
        }
    }

    /**
     * Computes the length of a given cloudlet (in MI) that has to be executed
     * since the last time cloudlets processing was updated.
     * 
     * @param rcl
     * @param currentTime current simulation time
     * @param mipsShare list with MIPS share of each Pe available to the scheduler
     * @return the executed length of the given cloudlet (in MI)
     * 
     * @see #updateCloudletsProcessing(double, java.util.List) 
     */
    protected long computeCloudletExecutionLengthForElapsedTime(ResCloudlet rcl, double currentTime, List<Double> mipsShare) {
        double timeSpam = currentTime - getPreviousTime();
        return (long)(getCapacity(mipsShare) * timeSpam * rcl.getNumberOfPes() * Consts.MILLION);
    }

    /**
     * Gets the individual MIPS capacity available for each PE available for the
     * scheduler, considering that all PEs have the same capacity.
     *
     * @param mipsShare list with MIPS share of each PE available to the
     * scheduler
     * @return the capacity of each PE
     */
    protected double getCapacity(List<Double> mipsShare) {
        double capacityOfAllPes = mipsShare.stream().reduce(0.0, Double::sum);
        currentCPUs = mipsShare.stream().filter(mips -> mips > 0).count();
        int pesInUse = getCloudletExecList().stream().mapToInt(rcl->rcl.getNumberOfPes()).sum();

        if (pesInUse > currentCPUs) {
            return capacityOfAllPes / pesInUse;
        }    

        return capacityOfAllPes / currentCPUs;
    }

    @Override
    public Cloudlet cloudletCancel(int cloudletId) {
        /**
         * @todo @author manoelcampos A lot of loops that could be
         * replaced by a single method that receives
         * the list of cloudlets to look at.
         */
        for (int i = 0; i < getCloudletFinishedList().size(); i++) {
            ResCloudlet rcl = getCloudletFinishedList().get(i);
            if (rcl.getCloudletId() == cloudletId) {
                getCloudletFinishedList().remove(rcl);
                return rcl.getCloudlet();
            }
        }

        for (int i = 0; i < getCloudletExecList().size(); i++) {
            ResCloudlet rcl = getCloudletExecList().get(i);
            if (rcl.getCloudletId() == cloudletId) {
                getCloudletExecList().remove(rcl);
                if (rcl.getRemainingCloudletLength() == 0) {
                    cloudletFinish(rcl);
                } else {
                    rcl.setCloudletStatus(Cloudlet.Status.CANCELED);
                }
                return rcl.getCloudlet();
            }
        }

        for (int i = 0; i < getCloudletPausedList().size(); i++) {
            ResCloudlet rcl = getCloudletPausedList().get(i);
            if (rcl.getCloudletId() == cloudletId) {
                rcl.setCloudletStatus(Cloudlet.Status.CANCELED);
                getCloudletPausedList().remove(rcl);
                return rcl.getCloudlet();
            }
        }

        return null;
    }

    @Override
    public boolean cloudletPause(int cloudletId) {
        for (int i = 0; i < getCloudletExecList().size(); i++) {
            ResCloudlet rcl = getCloudletExecList().get(i);
            if (rcl.getCloudletId() == cloudletId) {
                // remove cloudlet from the exec list and put it in the paused list
                getCloudletExecList().remove(rcl);
                if (rcl.getRemainingCloudletLength() == 0) {
                    cloudletFinish(rcl);
                } else {
                    rcl.setCloudletStatus(Cloudlet.Status.PAUSED);
                    getCloudletPausedList().add(rcl);
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public void cloudletFinish(ResCloudlet rcl) {
        rcl.setCloudletStatus(Cloudlet.Status.SUCCESS);
        rcl.finalizeCloudlet();
        getCloudletFinishedList().add(rcl);
    }

    @Override
    public double cloudletResume(int cloudletId) {
        for (int i = 0; i < getCloudletPausedList().size(); i++) {
            ResCloudlet rcl = getCloudletPausedList().get(i);
            if (rcl.getCloudletId() == cloudletId) {
                getCloudletPausedList().remove(rcl);
                rcl.setCloudletStatus(Cloudlet.Status.INEXEC);
                getCloudletExecList().add(rcl);

                // calculate the expected time for cloudlet completion
                // first: how many PEs do we have?
                double remainingLength = rcl.getRemainingCloudletLength();
                double estimatedFinishTime = CloudSim.clock()
                        + (remainingLength / (getCapacity(getCurrentMipsShare()) 
                        * rcl.getNumberOfPes()));

                return estimatedFinishTime;
            }
        }

        return 0.0;
    }

    @Override
    public double cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
        ResCloudlet rcl = new ResCloudlet(cloudlet);
        rcl.setCloudletStatus(Cloudlet.Status.INEXEC);
        for (int i = 0; i < cloudlet.getNumberOfPes(); i++) {
            rcl.setMachineAndPeId(0, i);
        }

        getCloudletExecList().add(rcl);

		// use the current capacity to estimate the extra amount of
        // time to file transferring. It must be added to the cloudlet length
        double extraSize = getCapacity(getCurrentMipsShare()) * fileTransferTime;
        long length = (long) (cloudlet.getCloudletLength() + extraSize);
        cloudlet.setCloudletLength(length);

        return cloudlet.getCloudletLength() / getCapacity(getCurrentMipsShare());
    }

    @Override
    public double cloudletSubmit(Cloudlet cloudlet) {
        return cloudletSubmit(cloudlet, 0.0);
    }

    @Override
    public int getCloudletStatus(int cloudletId) {
        for (ResCloudlet rcl : getCloudletExecList()) {
            if (rcl.getCloudletId() == cloudletId) {
                return rcl.getCloudletStatus().ordinal();
            }
        }
        
        for (ResCloudlet rcl : getCloudletPausedList()) {
            if (rcl.getCloudletId() == cloudletId) {
                return rcl.getCloudletStatus().ordinal();
            }
        }
        
        return -1;
    }

    @Override
    public double getTotalUtilizationOfCpu(double time) {
        double totalUtilization = 0;
        for (ResCloudlet gl : getCloudletExecList()) {
            totalUtilization += gl.getCloudlet().getUtilizationOfCpu(time);
        }
        return totalUtilization;
    }

    @Override
    public boolean areThereFinishedCloudlets() {
        return getCloudletFinishedList().size() > 0;
    }

    @Override
    public Cloudlet getNextFinishedCloudlet() {
        if (getCloudletFinishedList().size() > 0) {
            return getCloudletFinishedList().remove(0).getCloudlet();
        }
        return null;
    }

    @Override
    public int runningCloudletsNumber() {
        return getCloudletExecList().size();
    }

    @Override
    public Cloudlet migrateCloudlet() {
        ResCloudlet rgl = getCloudletExecList().remove(0);
        rgl.finalizeCloudlet();
        return rgl.getCloudlet();
    }

    /**
     * @todo If the method always return a empty list (that is created locally),
     * it doesn't make sense to exist. See other implementations such as
     * {@link CloudletSchedulerSpaceShared#getCurrentRequestedMips()}
     * @return
     */
    @Override
    public List<Double> getCurrentRequestedMips() {
        List<Double> mipsShare = new ArrayList<>();
        return mipsShare;
    }

    @Override
    public double getTotalCurrentAvailableMipsForCloudlet(ResCloudlet rcl, List<Double> mipsShare) {
        /*@todo It isn't being used any the the given parameters.*/
        return getCapacity(getCurrentMipsShare());
    }

    @Override
    public double getTotalCurrentAllocatedMipsForCloudlet(ResCloudlet rcl, double time) {
        //@todo The method is not implemented, in fact
        return 0.0;
    }

    @Override
    public double getTotalCurrentRequestedMipsForCloudlet(ResCloudlet rcl, double time) {
        //@todo The method is not implemented, in fact
        // TODO Auto-generated method stub
        return 0.0;
    }

    @Override
    public double getCurrentRequestedUtilizationOfRam() {
        double ram = 0;
        for (ResCloudlet cloudlet : cloudletExecList) {
            ram += cloudlet.getCloudlet().getUtilizationOfRam(CloudSim.clock());
        }
        return ram;
    }

    @Override
    public double getCurrentRequestedUtilizationOfBw() {
        double bw = 0;
        for (ResCloudlet cloudlet : cloudletExecList) {
            bw += cloudlet.getCloudlet().getUtilizationOfBw(CloudSim.clock());
        }
        return bw;
    }
}