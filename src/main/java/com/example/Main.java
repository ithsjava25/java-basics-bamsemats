package com.example;

import com.example.api.ElpriserAPI;

import java.rmi.ServerError;
import java.sql.SQLOutput;
import java.time.LocalDate;
import java.util.*;

import static java.lang.System.*;

public class Main {
    public static void printHelp() {
        System.out.println("""
            
            --- Enter arguments according to the following list ---
            
            --zone SE1|SE2|SE3|SE4
            --date YYYY-MM-DD (sets the date for price checking, if empty, current day will be selected)
            --sorted (display prices in descending order for selected date)
            --charging 2|4|8 (find optimal charging windows for selected date)
            --help (display usage information)
            
            """);
    }
    public static LocalDate getDate() {
        Scanner scanner =  new Scanner(System.in);
        LocalDate date = null;
        while (date == null) {
            System.out.print("Ange datum (YYYY-MM-DD, tomt för idag): ");
            String input = scanner.nextLine();
            try {
                date = input.isBlank() ? LocalDate.now() : LocalDate.parse(input);
            } catch (Exception e) {
                System.out.println("Felaktigt datum");
            }
        }
        return date;
    }
    public static String getZone() {
        Scanner scanner =  new Scanner(System.in);
        String zone = null;
        while (zone == null) {
            System.out.print("Vilken region (1,2,3 eller 4)? ");
            String input = scanner.nextLine();

            switch (input) {
                case "1" -> zone = "SE1";
                case "2" -> zone = "SE2";
                case "3" -> zone = "SE3";
                case "4" -> zone = "SE4";
                default -> System.out.println("Ogiltigt zonval. Välj 1, 2, 3 eller 4.");
            }
        }
        return zone;
    }
    public static int getTimmar() {
        Scanner scanner =  new Scanner(System.in);
        int timmar = 0;

        while (timmar == 0) {
            System.out.print("Ange laddtid (2, 4, 8 timmar): ");
            String input = scanner.nextLine();

            try {
                int value = Integer.parseInt(input);
                if (value == 2 || value == 4 || value == 8) {
                    timmar = value;
                } else {
                    System.out.println("Ogiltigt val. Välj 2, 4 eller 8 timmar");
                }
            } catch (NumberFormatException e) {
                System.out.println("Ogiltigt val. Välj 2, 4 eller 8 timmar. Endast heltal.");
            }
        }
        return timmar;
    }
    public static void getAveragePrice(LocalDate date, String zone, ElpriserAPI elpriserAPI) {

        List<ElpriserAPI.Elpris> enDagsPriser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));
        double prisGenomsnitt = 0.0;
        for (int i = 0; i < enDagsPriser.toArray().length; i++) {
            prisGenomsnitt += enDagsPriser.get(i).sekPerKWh();
        }
        prisGenomsnitt = prisGenomsnitt / enDagsPriser.size();
        System.out.printf("\n--- Genomsnittligt pris för %s i %s: %.4f SEK/kWh ---", date, zone, prisGenomsnitt);
        System.out.println("""
                                
                                
                                ------------------------------------------------------
                                
                                """);
    }
    public static void getCheapestCharging(LocalDate date, String zone, ElpriserAPI elpriserAPI, int timmar) {
        List<ElpriserAPI.Elpris> enDagsPriser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));

        double billigasteGenomsnitt = 0.0;
        String fromHour = "";
        String toHour = "";
        for (int i = 0; i <= enDagsPriser.size() - timmar; i++) {
            double sum = 0.0;
            for (int j = 0; j < timmar; j++) {
                sum += enDagsPriser.get(i + j).sekPerKWh();

            }

            if (billigasteGenomsnitt == 0.0) {
                billigasteGenomsnitt = (sum / timmar);
                fromHour = enDagsPriser.get(i).timeStart().toString().substring(11, 16);
                toHour = enDagsPriser.get(i + timmar - 1).timeStart().plusHours(1).toString().substring(11, 16);
            } else if (billigasteGenomsnitt > (sum / timmar)) {
                billigasteGenomsnitt = (sum / timmar);
                fromHour = enDagsPriser.get(i).timeStart().toLocalTime().toString();
                toHour = enDagsPriser.get(i + timmar - 1).timeStart().plusHours(1).toLocalTime().toString();
            }
        }

        System.out.printf("\n--- Billigaste genomsnittspris: %,.5f SEK/kWh ---\n--- Mellan timmarna %s och %s ---\n",
                billigasteGenomsnitt, fromHour, toHour);
        System.out.println("""
                                
                                
                                ------------------------------------------------------
                                
                                """);
    }
    public static void getSorted(LocalDate date, String zone, ElpriserAPI elpriserAPI) {
        List<ElpriserAPI.Elpris> enDagsPriser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));
        List<ElpriserAPI.Elpris> sorted = new ArrayList<>(enDagsPriser);
        sorted.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh));

        System.out.printf("\nPriser varje timme för %s ordnat från billigast till dyrast: \n\n", date);
        for (int i = 0; i < sorted.size(); i++) {
            String timme = sorted.get(i).timeStart().toString().substring(11, 16);
            Double pris = sorted.get(i).sekPerKWh();
            System.out.printf("Timme: %s || Pris: %.5f \n", timme, pris);
        }
        System.out.println("""
                                
                                
                                ------------------------------------------------------
                                
                                """);
    }
    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();
        LocalDate idag = LocalDate.now();
        Scanner scanner = new Scanner(System.in);
        LocalDate imorgon = LocalDate.now().plusDays(1);
        if (args.length > 0 && args[0].equals("--help")) {
            printHelp();
            return;
        }
        if (args.length < 1) {
            int choice = -1;
            while (choice != 0) {
                System.out.println("""
                        Välj ett alternativ:
                        1. Kolla genomsnittligt pris för en dag
                        2. Hitta optimal laddningsperiod (2h, 4h eller 8h)
                        3. Visa priser sorterade för en dag
                        4. Hjälp
                        0. Avsluta
                        """);
                System.out.println("Ditt val: ");;
                String choiceInput = scanner.nextLine();
                try {
                choice = Integer.parseInt(choiceInput);
                switch (choice) {
                    case 1 -> {
                        LocalDate date = getDate();
                        String zone = getZone();
                        getAveragePrice(date, zone, elpriserAPI);
                    }
                    case 2 -> {
                        LocalDate date = getDate();
                        String zone = getZone();
                        int timmar = getTimmar();
                        getCheapestCharging(date, zone, elpriserAPI, timmar);
                    }
                    case 3 -> {
                        LocalDate date = getDate();
                        String zone = getZone();
                        getSorted(date, zone, elpriserAPI);
                    }
                    case 4 -> {
                        printHelp();
                    }
                    case 0 -> {
                        try {
                            Thread.sleep(500);
                            System.out.println("Avslutar...");
                            Thread.sleep(500);
                            System.out.println("Tack for din tid..!");
                            Thread.sleep(500);
                            System.exit(1);
                        } catch(Exception e) {
                            System.out.println("Problem...");
                        }
                    }
                    default -> System.out.println("Felaktigt val.\n");
                }
                } catch (NumberFormatException e) {
                    System.out.println("Ogiltigt val.\n");
                    choice = -1;
                }


            }
        }

        String zone = null;
        LocalDate date = null;
        String charging = null;
        boolean sorted = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone":
                    if (i + 1 < args.length) {
                        zone = args[++i];
                    } else System.err.println("Felaktigt värde för --zone");
                    break;

                case "--date":
                    if (i + 1 < args.length) {
                        try {
                        date = LocalDate.parse(args[++i]);
                        } catch (Exception e) {
                            System.err.println("Felaktigt datum" + args[++i]);
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Felaktigt värde för --date");
                        System.exit(1);
                    }
                    break;

                case "--charging":
                    if (i + 1 < args.length) {
                        if (args[++i].equals("2") || args[++i].equals("4") || args[++i].equals("8")) {
                        charging = args[++i];
                        } else {
                            System.err.println("Felaktigt värde för --charging");
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Värde saknas för --charging");
                    }
                    break;

                case "--sorted":
                        sorted = true;
                    break;
            }
        }

        if (zone != null && date != null && charging != null) {
            getCheapestCharging(date, zone, elpriserAPI, Integer.parseInt(charging));
        } else if (zone != null && date != null && sorted) {
            getSorted(date, zone, elpriserAPI);
        } else if (zone != null && date != null) {
            getAveragePrice(date, zone, elpriserAPI);
        } else {
            printHelp();
        }



        /*if (zone == null) {
            System.out.print("Vilken region vill du ha elpriser för (1, 2, 3 eller 4)? ");
            String input = scanner.nextLine();
            zone = "SE" + input;
        }

        if (date == null) {
            System.out.println("Vilket datum vill du kolla? (YYYY-MM-DD) \n (Tomt för dagens datum) ");
            String input = scanner.nextLine();
            date = input.isBlank() ? idag : LocalDate.parse(input);
        }*/
        /*// ToDo: Här måste if-sats påbörjas, kan komma att behöva kasta om koden nedan för att passa i flow
        //  Skapa metoder för varje 'del' som kan köras!
        List<ElpriserAPI.Elpris> enDagsPriser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));
        List<ElpriserAPI.Elpris> dagensPriser = elpriserAPI.getPriser(idag, ElpriserAPI.Prisklass.valueOf(zone));
        List<ElpriserAPI.Elpris> morgondagensPriser = elpriserAPI.getPriser(imorgon, ElpriserAPI.Prisklass.valueOf(zone));

        System.out.print("\nHur många timmar vill du ladda? ");
        int antalTimmarLaddning = scanner.nextInt();

        // ToDo: Lista ut hur man kan lägga in föredragen tid att påbörja laddning...
        //      Stödja 2, 4 och 8 timmars laddningstid
        //      För arguments-delen av uppgiften:
        //          Initiera värdena som ska in som null. Följ sedan upp med if-satser
        //          där om värdena är null, så ska interface köras (menyn laddas, etc.) annars
        //          ska de relevanta metoderna köras.
        System.out.print("\nVilken timme vill du börja ladda? ");
        int startTid = scanner.nextInt();
        //Räknar ut billigaste timmarna och den genomsnittliga kostnaden per kWh
        double billigasteGenomsnitt = 0.0;
        String fromHour = "";
        String toHour = "";
        for (int i = startTid; i < dagensPriser.toArray().length - (antalTimmarLaddning - 1); i++) {
            double sum = 0.0;
            for (int j = 0; j < antalTimmarLaddning; j++) {
                sum += dagensPriser.get(i + j).sekPerKWh();

            }
            System.out.printf("\n%.4f", sum / antalTimmarLaddning);
            if (billigasteGenomsnitt == 0.0) {
                billigasteGenomsnitt = (sum / antalTimmarLaddning);
                fromHour = dagensPriser.get(i).timeStart().toString().substring(11, 16);
                toHour = dagensPriser.get(i + antalTimmarLaddning).timeStart().toString().substring(11, 16);
            } else if (billigasteGenomsnitt > (sum / antalTimmarLaddning)) {
                billigasteGenomsnitt = (sum / antalTimmarLaddning);
                fromHour = dagensPriser.get(i).timeStart().toLocalTime().toString();
                toHour = dagensPriser.get(i + antalTimmarLaddning).timeStart().toLocalTime().toString();
            }
        }
        System.out.printf("Billigaste genomsnittspris: %,.5f SEK/kWh\nMellan timmarna %s och %s\n", billigasteGenomsnitt, fromHour, toHour);


        *//* ToDo: Genomsnittligt pris för nuvarande 24-timmarsperioden
            Hitta billigaste resp. dyraste timmen på dygnet
            Skapa lista över alternativ (se README.md), vilken användaren svarar på för att få en specifik respons
        *//*
        // Printar ut tre tidpunkter och deras elpriser
        *//* dagensPriser.stream().limit(3).forEach(pris -> System.out.printf("Tid: %s, Pris: %.4f SEK/kWh\n", pris.timeStart().toLocalTime(), pris.sekPerKWh()));
        System.out.println(dagensPriser); */
    }

    // ToDo: --- Skapa metoder för repeterande sekvenser här ---


}
