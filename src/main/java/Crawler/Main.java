package Crawler;

import java.io.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        File f = new File("log.txt");



        Queue<String> list = new LinkedList<String>();
        try {
            Scanner reader = new Scanner(f);
            while(reader.hasNextLine())
            {
                list.add(reader.nextLine());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Change database data in Crawler.DatabaseActions to run
        Crawler crawl = new Crawler(list);
    }
}
