package com.fraudshield.txn.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A deterministic synthetic population of UPI users and merchants across Indian cities. */
final class Population {

    record City(String name, double lat, double lon) {
    }

    record Customer(String vpa, String device, City city, double lat, double lon, double typicalAmount,
                    List<String> favourites, boolean senior) {
    }

    static final List<City> CITIES = List.of(
            new City("Mumbai", 19.0760, 72.8777), new City("Delhi", 28.6139, 77.2090),
            new City("Bengaluru", 12.9716, 77.5946), new City("Hyderabad", 17.3850, 78.4867),
            new City("Chennai", 13.0827, 80.2707), new City("Kolkata", 22.5726, 88.3639),
            new City("Pune", 18.5204, 73.8567), new City("Jaipur", 26.9124, 75.7873),
            new City("Lucknow", 26.8467, 80.9462), new City("Ahmedabad", 23.0225, 72.5714));

    private static final String[] FIRST = {"aarav", "vivaan", "aditya", "vihaan", "arjun", "sai", "reyansh",
            "krishna", "ishaan", "rohan", "ananya", "diya", "aadhya", "saanvi", "pari", "anika", "navya", "riya",
            "meera", "kavya", "rahul", "priya", "amit", "sneha", "vikram", "pooja", "suresh", "lakshmi", "farhan",
            "zoya", "harpreet", "gurleen", "arun", "deepa", "manoj", "neha", "karthik", "divya", "imran", "sana"};
    private static final String[] LAST = {"sharma", "verma", "iyer", "reddy", "nair", "patel", "shah", "gupta",
            "singh", "khan", "das", "banerjee", "mehta", "joshi", "rao", "pillai", "kapoor", "malhotra", "bose",
            "chatterjee", "agarwal", "menon", "kulkarni", "deshmukh", "yadav"};
    private static final String[] BANKS = {"okhdfcbank", "okicici", "oksbi", "okaxis", "ybl", "paytm", "ibl", "axl"};

    static final List<String> MERCHANTS = List.of(
            "zomato@hdfcbank", "swiggy@icici", "blinkit@ybl", "zepto@axisbank", "bigbasket@hdfcbank",
            "irctc@sbi", "uber@axisbank", "rapido@ybl", "olacabs@icici", "jio.recharge@okaxis",
            "airtel.prepaid@icici", "bescom.billpay@sbi", "tatapower@hdfcbank", "mahanagargas@icici",
            "dmart.thane@icici", "reliancefresh@hdfcbank", "apollopharmacy@axisbank", "medplus@ybl",
            "bookmyshow@icici", "pvrcinemas@hdfcbank", "amazonpay@apl", "flipkart@axisbank", "myntra@icici",
            "chaipoint@ybl", "starbucks.in@hdfcbank", "dominos@icici", "haldirams@axisbank", "nykaa@ybl",
            "cult.fit@icici", "urbancompany@hdfcbank", "kirana.ramesh@paytm", "sharma.dairy@paytm",
            "auto.ravi@ybl", "chaiwala.kumar@paytm", "fuel.hpcl@sbi", "lic.premium@sbi", "school.fees@hdfcbank");

    final List<Customer> customers = new ArrayList<>();

    Population(int size, long seed) {
        Random rnd = new Random(seed);
        for (int i = 0; i < size; i++) {
            String first = FIRST[rnd.nextInt(FIRST.length)];
            String last = LAST[rnd.nextInt(LAST.length)];
            String vpa = first + "." + last + (10 + i) + "@" + BANKS[rnd.nextInt(BANKS.length)];
            City city = CITIES.get(rnd.nextInt(CITIES.size()));
            // Log-normal spend: most payments are a few hundred rupees, a long tail into thousands.
            double typical = Math.exp(5.0 + rnd.nextGaussian() * 0.6);
            List<String> favourites = new ArrayList<>();
            for (int f = 0; f < 6; f++) favourites.add(MERCHANTS.get(rnd.nextInt(MERCHANTS.size())));
            customers.add(new Customer(vpa, "dev-" + Integer.toHexString(vpa.hashCode() & 0xfffffff), city,
                    city.lat() + rnd.nextGaussian() * 0.04, city.lon() + rnd.nextGaussian() * 0.04,
                    typical, favourites, rnd.nextDouble() < 0.12));
        }
        // Give everyone a couple of friends to pay (P2P).
        for (Customer c : new ArrayList<>(customers)) {
            for (int f = 0; f < 2; f++) c.favourites().add(customers.get(rnd.nextInt(customers.size())).vpa());
        }
    }
}
