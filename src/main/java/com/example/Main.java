package com.example;

import com.example.api.ElpriserAPI;

import java.time.LocalDate;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();
        LocalDate idag = LocalDate.now();
        List<ElpriserAPI.Elpris> dagensPriser = elpriserAPI.getPriser(idag, ElpriserAPI.Prisklass.SE3);
        dagensPriser.stream().limit(3).forEach(pris -> System.out.printf("Tid: %s, Pris: %.4f SEK/kWh\n", pris.timeStart().toLocalTime(), pris.sekPerKWh()));
    }
}
