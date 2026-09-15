package de.tu_dresden.lat.api;
import java.util.concurrent.CountDownLatch;

import de.tu_dresden.lat.ELExplicator;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class ElExplicatorApplication extends Application<ElExplicatorConfiguration>{
    private static RepairSession repairSession;
    private static CountDownLatch shutdownLatch;
    public static void main(String []args) throws Exception{
        new ElExplicatorApplication().run(args);
    }

    @Override
    public String getName(){
        return "ElExplicator";
    }

    @Override
    public void initialize(Bootstrap<ElExplicatorConfiguration> bootstrap){
        super.initialize(bootstrap);
    }

    public static void setRepairSession(RepairSession session){
        repairSession = session;
    }

    public static void setShutdownLatch(CountDownLatch latch){
        shutdownLatch = latch;
    }

    public static CountDownLatch getShutDownLatch(){
        return shutdownLatch;
    }
    @Override
    public void run(ElExplicatorConfiguration config, Environment environment) throws Exception{
        //do nothing for now
        final ElExplicatorResources resource = new ElExplicatorResources(repairSession);
        environment.jersey().register(resource);
    }
}
