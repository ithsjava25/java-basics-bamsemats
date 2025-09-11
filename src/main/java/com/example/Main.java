package com.example;

import com.example.api.ElpriserAPI;

import java.rmi.ServerError;
import java.sql.Array;
import java.sql.SQLOutput;
import java.time.LocalDate;
import java.util.*;

import static java.lang.System.*;
// ToDo: Fix all the issues with tests (mostly formalia?)
//  Get assignment turned in and do other tasks (practice for loops and arrays)
//  Fix charging hours - they should look into up to 8 hours into the following day and find the cheapest window
//  This will probably entail creating a new array of the given date and the following one's first hours...
public class Main {
    static Locale locale = new Locale("sv", "SE");
    public static void printHelp() {
        System.out.println("""
            
            --- Enter arguments according to the following list ---
            
            --zone SE1|SE2|SE3|SE4
            --date YYYY-MM-DD (sets the date for price checking, if empty, current day will be selected)
            --sorted (display prices in descending order for selected date)
            --charging 2|4|8 (find optimal charging windows for selected date)
            --minmax (shows the cheapest and most expensive hour respectively for selected date)
            --help (display usage information)
            --menu (interactive menu)
            
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
            System.out.print("Ange laddtid (2h, 4h, eller 8h): ");
            String input = scanner.nextLine();

            try {
                int value = Integer.parseInt(input.substring(0, 1));
                if (value == 2 || value == 4 || value == 8) {
                    timmar = value;
                } else {
                    System.out.println("Ogiltigt val. Välj 2h, 4h eller 8h.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Ogiltigt val. Välj 2h, 4h eller 8h.");
            }
        }
        return timmar;
    }
    public static void getAveragePrice(LocalDate date, String zone, ElpriserAPI elpriserAPI) {
        List<ElpriserAPI.Elpris> enDagsPriser;
        try {
            enDagsPriser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));
        } catch (Exception e) {
            System.err.println("Fel vid hämtning av priser för " + date);
            return;
        }

        if (enDagsPriser.isEmpty()) {
            System.out.println("Inga priser tillgängliga för " + date);
            return;
        }

        double prisGenomsnitt = 0.0;
        double sum = 0.0;
        double minPrice = Double.parseDouble(getMinMax(date, zone, elpriserAPI)[0]);
        double maxPrice = Double.parseDouble(getMinMax(date, zone, elpriserAPI)[1]);
        String minHourStart = getMinMax(date, zone, elpriserAPI)[2].substring(11, 13);
        String maxHourStart = getMinMax(date, zone, elpriserAPI)[3].substring(11, 13);
        String minHourEnd = getMinMax(date, zone, elpriserAPI)[4].substring(11, 13);
        String maxHourEnd = getMinMax(date, zone, elpriserAPI)[5].substring(11, 13);

        for (int i = 0; i < enDagsPriser.toArray().length; i++) {
            sum += enDagsPriser.get(i).sekPerKWh();
        }
        prisGenomsnitt = sum / enDagsPriser.size();
        System.out.printf(locale, "\n--- Lägsta pris för %s kl %s-%s i %s: %,.2f öre/kWh ---", date, minHourStart, minHourEnd, zone, minPrice*100);
        System.out.printf(locale, "\n--- Medelpris för %s i %s: %,.2f öre/kWh ---", date, zone, prisGenomsnitt*100);
        System.out.printf(locale, "\n--- Högsta pris för %s kl %s-%s i %s: %,.2f öre/kWh ---", date, maxHourStart, maxHourEnd, zone, maxPrice*100);
        System.out.println("""
                                
                                
                                """);
    }
    public static void getCheapestCharging(LocalDate date, String zone, ElpriserAPI elpriserAPI, int timmar) {
        List<ElpriserAPI.Elpris> dagensPriser;
        try {
            dagensPriser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));
        } catch (Exception e) {
            System.err.println("Fel vid hämtning av priser för " + date);
            return;
        }

        if (dagensPriser.isEmpty()) {
            System.out.println("Inga priser tillgängliga för " + date);
            return;
        }

        List<ElpriserAPI.Elpris> morgondagensPriser = new ArrayList<>();
        try {
            morgondagensPriser = elpriserAPI.getPriser(date.plusDays(1), ElpriserAPI.Prisklass.valueOf(zone));
        } catch (Exception e) {
            System.out.println("Obs: Kunde inte hämta morgondagens priser, beräknar bara för en dag.");
        }
        List<ElpriserAPI.Elpris> priser48h = new ArrayList<>(dagensPriser);
        priser48h.addAll(morgondagensPriser);

        if (priser48h.size() < timmar) {
            System.err.println("Inte tillräckligt med data för att beräkna laddningsfönster.");
            return;
        }

        double billigasteGenomsnitt = Double.MAX_VALUE;

        String fromHour = "";
        String toHour = "";
        int startIndex = 0;
        for (int i = 0; i <= priser48h.size() - timmar; i++) {
            double sum = 0.0;
            for (int j = 0; j < timmar; j++) {
                sum += priser48h.get(i + j).sekPerKWh();
            }

            if (billigasteGenomsnitt > (sum / timmar)) {
                billigasteGenomsnitt = (sum / timmar);
                startIndex = i;
            }
        }
        ElpriserAPI.Elpris start = priser48h.get(startIndex);
        ElpriserAPI.Elpris end   = priser48h.get(startIndex + timmar - 1);
        fromHour = start.timeStart().toString().substring(11, 13) + ":00";
        toHour = end.timeEnd().toString().substring(11, 13) + ":00";

        System.out.printf(locale, "\n--- Billigaste Medelpris för fönster: %,.2f öre / kWh ---\n--- Påbörja laddning kl %s. Avslutas kl %s ---\n",
                billigasteGenomsnitt * 100, fromHour, toHour);
        System.out.println("""
                                
                                
                                ------------------------------------------------------
                                
                                """);
    }
    public static String[] getMinMax(LocalDate date, String zone, ElpriserAPI elpriserAPI) {
        List<ElpriserAPI.Elpris> enDagsPriser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));

        double billigastePriset = 0.0;
        double dyrastePriset = 0.0;
        String billigasteTimmen = "";
        String billigasteTimmenEnd = "";
        String dyrasteTimmenEnd = "";
        String dyrasteTimmen = "";
        String[] minMax = new String[6];

        for (int i = 0; i < enDagsPriser.size(); i++) {
            if (billigastePriset == 0) {
                billigastePriset =  enDagsPriser.get(i).sekPerKWh();
                billigasteTimmen = enDagsPriser.get(i).timeStart().toString();
                billigasteTimmenEnd = enDagsPriser.get(i).timeEnd().toString();
            } else {
                if (billigastePriset > enDagsPriser.get(i).sekPerKWh()) {
                    billigastePriset = enDagsPriser.get(i).sekPerKWh();
                    billigasteTimmen = enDagsPriser.get(i).timeStart().toString();
                    billigasteTimmenEnd = enDagsPriser.get(i).timeEnd().toString();
                }
            }
        }
        for (int i = 0; i < enDagsPriser.size(); i++) {
            if (dyrastePriset < enDagsPriser.get(i).sekPerKWh()) {
                dyrastePriset = enDagsPriser.get(i).sekPerKWh();
                dyrasteTimmen = enDagsPriser.get(i).timeStart().toString();
                dyrasteTimmenEnd = enDagsPriser.get(i).timeEnd().toString();
            }
        }
        minMax[0] = Double.toString(billigastePriset);
        minMax[1] = Double.toString(dyrastePriset);
        minMax[2] = billigasteTimmen;
        minMax[3] = dyrasteTimmen;
        minMax[4] = billigasteTimmenEnd;
        minMax[5] = dyrasteTimmenEnd;
        return minMax;
    }
    public static void getCheapestAndMostExpensive(LocalDate date, String zone, ElpriserAPI elpriserAPI) {
        List<ElpriserAPI.Elpris> enDagsPriser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));

        double billigastePriset = 0.0;
        double dyrastePriset = 0.0;
        String dyrasteTimmen = "";
        String billigasteTimmen = "";

        for (int i = 0; i < enDagsPriser.size(); i++) {
            if (billigastePriset == 0) {
                billigastePriset =  enDagsPriser.get(i).sekPerKWh();
                billigasteTimmen = enDagsPriser.get(i).timeStart().toString();
            } else {
                if (billigastePriset > enDagsPriser.get(i).sekPerKWh()) {
                    billigastePriset = enDagsPriser.get(i).sekPerKWh();
                    billigasteTimmen = enDagsPriser.get(i).timeStart().toString();
                }
            }
        }
        for (int i = 0; i < enDagsPriser.size(); i++) {
            if (dyrastePriset < enDagsPriser.get(i).sekPerKWh()) {
                dyrastePriset = enDagsPriser.get(i).sekPerKWh();
                dyrasteTimmen = enDagsPriser.get(i).timeStart().toString();
            }
        }

        System.out.printf(locale, "\nLägsta pris denna dag var %s SEK/kWh klockan %s\n", billigastePriset, billigasteTimmen.substring(11, 16));
        System.out.printf(locale, "\nHögsta pris för denna dag var %s SEK/kWh klockan %s\n", dyrastePriset, dyrasteTimmen.substring(11, 16));
        System.out.println("""
                                
                                
                                ------------------------------------------------------
                                
                                """);
    }
    public static void getSorted(LocalDate date, String zone, ElpriserAPI elpriserAPI) {
        List<ElpriserAPI.Elpris> enDagsPriser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));
        List<ElpriserAPI.Elpris> sorted = new ArrayList<>(enDagsPriser);
        sorted.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh));

        System.out.printf(locale, "\nPriser varje timme för %s ordnat från billigast till dyrast: \n\n", date);
        for (int i = 0; i < sorted.size(); i++) {
            String timme = sorted.get(i).timeStart().toString().substring(11, 16);
            String timmeEnd = sorted.get(i).timeEnd().toString().substring(11, 16);
            Double pris = sorted.get(i).sekPerKWh();
            System.out.printf(locale, "%s-%s %,.2f öre \n", timme.substring(0, 2), timmeEnd.substring(0, 2), pris * 100);
        }
        System.out.println("""
                                
                                
                                ------------------------------------------------------
                                
                                """);
    }
    public static void runInteractiveMenu(Scanner scanner, ElpriserAPI elpriserAPI) {
        int choice = -1;
        while (choice != 0) {
            System.out.println("""
                        Välj ett alternativ:
                        1. Kolla lägsta, medel och högsta pris för en dag
                        2. Hitta optimal laddningsperiod (2h, 4h eller 8h)
                        3. Visa priser sorterade för en dag
                        4. Visa en dags billigaste resp dyraste timme
                        5. Hjälp
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
                        LocalDate date = getDate();
                        String zone = getZone();
                        getCheapestAndMostExpensive(date, zone, elpriserAPI);
                    }
                    case 5 -> {
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
    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();
        LocalDate idag = LocalDate.now();
        Scanner scanner = new Scanner(System.in);
        LocalDate imorgon = LocalDate.now().plusDays(1);
        if (Arrays.asList(args).contains("--help") || args.length == 0) {
            printHelp();
            return;
        }
        if (Arrays.asList(args).contains("--menu")) {
            int choice = -1;
            while (choice != 0) {
                System.out.println("""
                        Välj ett alternativ:
                        1. Kolla lägsta, medel och högsta pris för en dag
                        2. Hitta optimal laddningsperiod (2h, 4h eller 8h)
                        3. Visa priser sorterade för en dag
                        4. Visa en dags billigaste resp dyraste timme
                        5. Hjälp
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
                        LocalDate date = getDate();
                        String zone = getZone();
                        getCheapestAndMostExpensive(date, zone, elpriserAPI);
                    }
                    case 5 -> {
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
        boolean minmax = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone":
                    if (i + 1 < args.length) {
                        switch (args[++i]) {
                            case "SE1": zone = "SE1";
                                break;
                            case "SE2": zone = "SE2";
                                break;
                            case "SE3": zone = "SE3";
                                break;
                            case "SE4": zone = "SE4";
                                break;
                            default: zone = null;
                        }
                    } else {
                        System.err.println("Ogiltig zon");
                        System.out.println("Ogiltig zon");
                    }
                    break;

                case "--date":
                    if (i + 1 < args.length) {
                        try {
                        String element = args[++i];
                        date = LocalDate.parse(element);
                        } catch (Exception e) {
                            System.out.println("Ogiltigt datum angivet, använder dagens datum.");
                            date = LocalDate.now();
                        }
                    } else {
                        date = LocalDate.now();
                    }
                    break;

                case "--charging":
                    if (i + 1 < args.length) {
                        String element = args[++i];
                        if (element.equals("2h") || element.equals("4h") || element.equals("8h")) {
                        charging = element.substring(0, 1);
                        } else {
                            System.err.println("Felaktigt värde för --charging");
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Värde saknas för --charging");
                    }
                    break;

                case "--minmax":
                        minmax = true;
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
        } else if (zone != null && date != null && minmax) {
            getCheapestAndMostExpensive(date, zone, elpriserAPI);
        } else if (zone != null && date != null) {
            getAveragePrice(date, zone, elpriserAPI);
        } else if (date != null && zone == null){
            System.out.println("Ogiltig zon, zone required");
        } else if (date == null && zone != null) {
            date = LocalDate.now();
            getAveragePrice(date, zone, elpriserAPI);
        } else {
            printHelp();
            System.exit(1);
        }
    }
}
