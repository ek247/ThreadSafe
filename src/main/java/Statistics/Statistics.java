package Statistics;
import Crawler.DatabaseActions;
import edu.cmu.lti.jawjaw.db.SQL;
import edu.cmu.lti.lexical_db.ILexicalDatabase;
import edu.cmu.lti.lexical_db.NictWordNet;
import edu.cmu.lti.ws4j.impl.WuPalmer;
import edu.cmu.lti.ws4j.util.WS4JConfiguration;
import org.junit.runner.Result;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Eugene Kennedy on 6/3/2016.
 * Use WS4J to generate statistics and search for items in database based on semantic similarity.
 *
 */

public class Statistics {
    final String categories = "dress pants jewelry shirt skirt leggings belt shorts";

    DatabaseActions actions;
    ILexicalDatabase db;

    public Statistics()
    {
        actions = new DatabaseActions();
        db = new NictWordNet();
    }

    /*public static void main(String [] args)
    {
        Statistics s = new Statistics();
        try {
            System.out.println(s.getInfoBySimilarity(0, "dress").get(0).getName());
            HashMap<String, Integer> map = s.popularityByActualName();
            for(String str : map.keySet())
                System.out.println(str + " : " + map.get(str));
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            System.err.println("oops");
        }
        //System.out.println(s.compute("dress#n#1", "skirt#n#1"));
    }
*/
    public ArrayList<ProductData> getInfoByName(String name) throws SQLException
    {
        ArrayList<ProductData> data = new ArrayList<ProductData>();

        PreparedStatement stmt = actions.conn.prepareStatement("select pid, name, url, colorName, descFull, price from items where name like '%?%'");
        stmt.setString(1, name);
        ResultSet rs = stmt.executeQuery();
        while(rs.next())
        {
            ProductData info = new ProductData();
            info.setPid(rs.getInt("pid"));
            info.setColor(rs.getString("colorName"));
            info.setDesc(rs.getString("descFull"));
            info.setPrice(rs.getFloat("price"));
            data.add(info);
        }

        return data;
    }

    //id -> 0 = name, 1 = Full Description, 2 = Color
    public ArrayList<ProductData> getInfoBySimilarity(int id, String name) throws SQLException
    {
        ArrayList<ProductData> data = new ArrayList<ProductData>();

        PreparedStatement stmt = null;
        String column = "";
        switch (id) {
            case 0:
                stmt = actions.conn.prepareStatement("select pid, name from items");
                break;
            case 1:
                stmt = actions.conn.prepareStatement("select pid, descFull from items");
                break;
            case 2:
                stmt = actions.conn.prepareStatement("select pid, colorName from items");
                break;
        }
        ResultSet rs = stmt.executeQuery();
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<Integer> pid = new ArrayList<Integer>();

        while(rs.next())
        {
            pid.add(rs.getInt("pid"));
            names.add(rs.getString(2));
        }

        stmt.close();
        rs.close();

        String [] givenNameArr = name.split(" |\\.|,|-");


        //Iterate over found names and compute similarity wordwise accross given name
        for(int i = 0; i < names.size(); i++)
        {
            int [] matches = new int [givenNameArr.length];
            double [] sum = new double [givenNameArr.length];
            String [] arr = names.get(i).split(" |\\.|,|-");
            for(int j = 0; j < arr.length; j++)
            {
                String strings = arr[j];
                for(int k = 0; k < givenNameArr.length; k++)
                {
                    String test = givenNameArr[k];
                    double val = compute(test, strings);
                    if(val > .6) //Use .6+ as similar enough to be considered a "match"
                    {
                        sum[k] += val;
                        matches[k] += 1;
                    }
                }
            }

            //Weight results
            double weight = 0;
            for(int w = 0; w < givenNameArr.length; w++)
            {

                if(matches[w] == 0)
                    weight += 0; //Not found, reduce weight
                else
                    weight += sum[w]/matches[w]; //Otherwise, change weight by the average strength of the match

            }
            if(weight > .5) //If overall weight is above .5, return it.
            {
                ProductData info = new ProductData();
                info.setPid(pid.get(i));
                info.setName(names.get(i));
                info.setWeight(weight);
                data.add(info);
            }
        }

        //For found products,
        for(ProductData info : data)
        {
            stmt = actions.conn.prepareStatement("select price, descFull, url, colorName from items where pid=?");
            stmt.setInt(1, info.getPid());
            rs = stmt.executeQuery();

            while(rs.next())
            {
                info.setPrice(rs.getFloat("price"));
                info.setDesc(rs.getString("descFull"));
                info.setURL(rs.getString("url"));
                info.setColor(rs.getString("colorName"));
            }

            stmt.close();
            rs.close();
        }

        return data;
    }

    //Compute similarity of words using ws4j as a double between 0 and 1.
    private double compute(String word1, String word2) {
        WS4JConfiguration.getInstance().setMFS(true);
        double s = new WuPalmer(db).calcRelatednessOfWords(word1, word2);

        if(s > 1)
            s = 1; //if words are equal sometimes it can go above 1 in testing. Don't allow this.
        return s;
    }

    //Generates a hashmap of names/item terms and the number of occurences. This is not based on semantics, but words must be exact to be counted.
    public HashMap<String, Integer> popularityByActualName()
    {
        HashMap<String, Integer> map = new HashMap<String, Integer>();

        try {
            PreparedStatement stmt = actions.conn.prepareStatement("select name, descFull, colorName from items");
            ResultSet rs = stmt.executeQuery();
            while(rs.next())
            {
                String name = (rs.getString("name"));
                String desc = (rs.getString("descFull"));
                String color = (rs.getString("colorName"));

                for(String s : name.split("\\.|,|-| "))
                {
                    if(map.containsKey(s)) {
                        map.put(s, new Integer(map.get(s).intValue() + 1));
                        continue;
                    }
                    else
                        map.put(s, new Integer(1));
                }

                for(String s : desc.split(" |\\.|,|-"))
                {
                    if(s.equals(""))
                        continue;
                    if(map.containsKey(s)) {
                        map.put(s, new Integer(map.get(s).intValue() + 1));
                        continue;
                    }
                    else
                        map.put(s, new Integer(1));
                }

                for(String s : color.split(" |\\.|,|-"))
                {
                    if(map.containsKey(s)) {
                        map.put(s, new Integer(map.get(s).intValue() + 1));
                        continue;
                    }
                    else
                        map.put(s, new Integer(1));
                }

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }


        return map;
    }
}
