package com.fraudshield.txn.api;

import com.fraudshield.txn.sim.Scenario;
import com.fraudshield.txn.sim.SimulatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final SimulatorService simulator;

    public SimulatorController(SimulatorService simulator) {
        this.simulator = simulator;
    }

    @GetMapping
    public SimulatorService.Status status() {
        return simulator.status();
    }

    @GetMapping("/demo-customer")
    public SimulatorService.DemoCustomer demoCustomer() {
        return simulator.demoCustomer();
    }

    @PostMapping("/start")
    public SimulatorService.Status start(@RequestBody(required = false) StartRequest req) {
        StartRequest r = req == null ? new StartRequest(8.0, 0.04) : req;
        return simulator.start(r.tps() == null ? 8 : r.tps(), r.fraudRate() == null ? 0.04 : r.fraudRate());
    }

    @PostMapping("/stop")
    public SimulatorService.Status stop() {
        return simulator.stop();
    }

    @PostMapping("/inject/{scenario}")
    public SimulatorService.Status inject(@PathVariable Scenario scenario) {
        return simulator.inject(scenario);
    }

    @PostMapping("/reset-scoreboard")
    public SimulatorService.Status reset() {
        simulator.resetScoreboard();
        return simulator.status();
    }

    public record StartRequest(Double tps, Double fraudRate) {
    }
}
