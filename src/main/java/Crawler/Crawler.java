package Crawler; /**
 * Created by worri on 4/4/2016.
 */
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.SocketTimeoutException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Queue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;



public class Crawler {
    private final String insertSQL = "INSERT INTO `crawler`.`items`\n" +
            "(`pid`,\n" +
            "`url`,\n" +
            "`colorCode`,\n" +
            "`colorName`,\n" +
            "`price`,\n" +
            "`descFull`,\n" +
            "`name`,\n" +
            "`category`,\n" +
            "`site`)\n" +
            "VALUES\n" +
            "(?,\n" +
            "?,\n" +
            "?,\n" +
            "?,\n" +
            "?,\n" +
            "?,\n" +
            "?,\n" +
            "?,\n" +
            "?);";

    private final String updateSQL =  "UPDATE items set colorCode = ?, colorName = ?, url = ?, price = ?, descFull = ?, name = ?, category=? where pid=? and site=?";
    private DatabaseActions db;
    private Queue<String> toVisit;
    private HashSet<String> hasVisited;

    public Crawler(Queue<String> list)
    {
        db = new DatabaseActions();
        toVisit = list;
        hasVisited = new HashSet<String>();
        try {
            crawl();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        finally {
            printLog();
        }
    }

    private void printLog() {
        File f = new File("log.txt");
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(f));

        for(String t : toVisit)
        {
            writer.println(t);
            writer.flush();
            //Pick up where we were later, but don't preserve hasVisited to allow us to update our stuff later.
        }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void crawl()
    {
        while(!toVisit.isEmpty())
        {
            processPage(toVisit.remove());
        }
    }

    //Process the page we are currently visiting. If already visited, don't do anything. If its a product, add it to the database.
    public void processPage(String url)
    {

        url = parseURL(url);
        System.out.println("here at "+url);
        if(hasVisited.contains(url))
            return;

        hasVisited.add(url);

        //System.out.println("here + "+url);
        if(url.equals(""))
            return;


        //System.out.println("here");

        try {
            if (url.contains("bananarepublic")) {
                processPageBR(url);
            } else if (url.contains("jcrew")) {
                //System.out.println("here");
                processPageJCrew(url);
            }
        }
        catch(SocketTimeoutException e)
        {
            System.err.println("Socket timed out!");
        }
        catch (IOException e) {
            System.err.println("Couldn't connect to page!");
            return; //Don't add to visited as it may just be a temporary problem
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private void processPageJCrew(String url) throws SocketTimeoutException, IOException, SQLException, java.net.MalformedURLException{

            Document doc = Jsoup.connect(url).get();
            Elements questions = doc.select("a[href]");
            for(Element link : questions) {
                toVisit.add(link.attr("abs:href"));
            }

            if(url.contains("PRDOVR")) //Is a product, strip out pid
            {

                System.out.println("at product");
                String pid = "";
                for(int i = url.indexOf("~")+1; i < url.length()-1; i++)//May need to add one
                {
                    String tmp = url.substring(i, i+1);
                    if(tmp.equals("/"))
                        break;
                    else
                        pid+=tmp;
                }



                doc = Jsoup.connect("https://www.jcrew.com/data/v1/US/products/" + pid).ignoreContentType(true).get();
                //Get url
                String html = (doc.html().replace("\"", "")).replace("{", "").replace("}", "");
                String toDBUrl = extractJCrewInfo(html, "canonicalUrl");
                //Determine gender from URL, as it is always an empty string from JCrew api
                String gender = "";
                if(toDBUrl.contains("women") || toDBUrl.contains("Women") || toDBUrl.contains("WOMEN"))
                    gender = "women";
                else if(toDBUrl.contains("men") || toDBUrl.contains("men") || toDBUrl.contains("MEN"))
                    gender = "men";
                int start = toDBUrl.indexOf("/");
                String category = toDBUrl.substring(start+1, toDBUrl.indexOf("/", start+1));
                toDBUrl = "https://www.jcrew.com/"+toDBUrl;
                String descLengthy = extractJCrewInfo(html, "productDescriptionRomance").replace("[", "").replace("]", "");
                String name = gender + " " +extractJCrewInfo(html, "productName");
                String listPriceTmp = extractJCrewInfo(html, "listPrice");
                float listPrice = new Float(listPriceTmp.replace("amount:", ""));
                String color = extractJCrewInfo(html, "colors:").replace("[", "").replace("]", "") + ",";
                String colorName = "";
                String colorCode = "";
                //get Colors
                int i = color.indexOf("code:");

                do
                {
                    colorCode += color.substring(i, color.indexOf(",", i))+" ";
                    i = color.indexOf("name:", i);
                    colorName += color.substring(i, color.indexOf(",", i))+" ";
                    i = color.indexOf("code:", i);
                }
                while(i != -1);

                colorName = colorName.replace("name:","");
                colorCode = colorCode.replace("code:" , "");

                /*
                System.out.println(colorCode);
                System.out.println(colorName);
                System.out.println(listPrice);
                System.out.println(name);
                System.out.println(descLengthy);
                System.out.println(descTechy);
                System.out.println("Url: " + toDBUrl);
                */

                //Check whether to update or insert
                PreparedStatement stmt = db.conn.prepareStatement("select COUNT(pid) as count from items where pid=? and site='jcrew'");
                stmt.setString(1, pid);

                ResultSet rs = stmt.executeQuery();
                boolean update = true;
                while(rs.next()) {
                    int count = rs.getInt("count");
                    System.out.println(pid + " " + count);
                    if (count == 0)
                        update = false;

                }


                rs.close();
                stmt.close();

                //Add to database
                String sql = "";
                if(!update) {
                    sql = insertSQL;
                    insertInto(colorCode, colorName, listPrice, name, descLengthy, toDBUrl, pid, "jcrew", category, sql);
                }
                else
                {
                    sql = updateSQL;
                    update(colorCode, colorName, listPrice, name, descLengthy, toDBUrl, pid, "jcrew", category, sql);
                }


            }
    }

    private void update(String colorCode, String colorName, float listPrice, String name, String descLengthy, String toDBUrl,
                        String pid, String site, String category, String sql) throws SQLException
    {
        PreparedStatement stmt = db.conn.prepareStatement(sql);
        stmt.setString(8, pid);
        stmt.setString(9, site);
        stmt.setString(3, toDBUrl);
        stmt.setString(1, colorCode);
        stmt.setString(2, colorName);
        stmt.setFloat(4, listPrice);
        stmt.setString(5, descLengthy);
        stmt.setString(6, name);
        stmt.setString(7, category);

//        stmt.execute();
        stmt.executeUpdate();
        stmt.close();

    }


    private void insertInto(String colorCode, String colorName, float listPrice, String name, String descLengthy,
                                 String toDBUrl, String pid, String site, String category, String sql)
    throws SQLException
    {
        PreparedStatement stmt = db.conn.prepareStatement(sql);


        stmt.setString(1, pid);
        stmt.setString(2, toDBUrl);
        stmt.setString(3, colorCode);
        stmt.setString(4, colorName);
        stmt.setFloat(5, listPrice);
        stmt.setString(6, descLengthy);
        stmt.setString(7, name);
        stmt.setString(8, category);
        stmt.setString(9, site);

        stmt.executeUpdate();
        stmt.close();
    }

    //Extracts the url and other info of the JCrew product
    private String extractJCrewInfo(String html, String info) throws StringIndexOutOfBoundsException{
        String toRet = "";
        int index = html.indexOf(info);
        if(index == -1)
            return "";

        int start = html.indexOf(":", index);
        int end = html.indexOf(",", index);
        if(start == -1 || end == -1)
            return "";
        toRet = html.substring(start+1, end);

        //gross
        if(info.equals("colors:"))
        {
            String tmp = html.substring(start, html.length());
            tmp = tmp.substring(1, tmp.indexOf("]"));
            toRet = tmp;
        }


        return toRet;
    }

    //trim URL to standard form and make sure not to visit pages we don't want
    private String parseURL(String url) {
        String toRet = url;
        if(toRet.contains("jcrew"))
        {
            if(toRet.contains("account") || toRet.contains("signin"))
                toRet = "";

        }
        else if(toRet.contains("bananarepublic"))
        {
            if(toRet.contains("profile"))
                toRet = "";
            if(toRet.contains("#"))
                toRet = toRet.substring(0, toRet.indexOf("#"));
        }

        //More conditions to be added
        return toRet;
    }

    //Process bananarepublic page
    private void processPageBR(String url) throws SocketTimeoutException, IOException, SQLException, java.net.MalformedURLException
    {

        Document doc = Jsoup.connect(url).header("Accept-Encoding", "gzip, deflate")
                .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:23.0) Gecko/20100101 Firefox/23.0")
                .maxBodySize(0)
                .timeout(2000)
                .get();
        Elements questions = doc.select("a[href]");
        for(Element link : questions) {
            toVisit.add(link.attr("abs:href"));
        }



        if(url.contains("pid"))
        {
            int start = -1;
            int end = -1;
            String html = "";
            questions = doc.select("#bodyContainer > #mainContent");
            String category = "";
            try {
                category = extractBRInfo(doc.html().replace("\"", ""), "<meta name=keywords", ">");
            }
            catch(StringIndexOutOfBoundsException e)
            {
                return;
            }

                //System.out.println("Elements found = " + questions.size());
                //for(Element el : questions)
                //    System.out.println(el.html());


                html = questions.html().replace("\"", "");


                start = html.indexOf("gap.pageProductData =");
                end = html.indexOf("gap.currentBrand", start);
                //System.out.println("Start: "+ start +", " + end);

            if(start == -1 && end == -1) {//Can't get product data, come back for it later.
                hasVisited.remove(url);
                toVisit.add(url);
                return;
            }
            //System.out.println("Start: "+ start +", " + end);
            html = html.substring(start, end);

            //System.out.println(html);


            /*int start = html.indexOf("gap.pageProductData =");
            System.out.println("start = " + start);
            if(start == -1) {
                System.out.println("in here");
                while (start != -1) {
                    html = Jsoup.connect(url).get().html();
                    start = html.indexOf("gap.pageProductData =");
                    System.out.print("no product data " + start);
                }
            }
            while(start == -1)
            {
                System.out.println("how.");
                return;
            }

            int end = html.indexOf("<", start)-start;
            html = html.substring(start);
            html = html.substring(0, end);
            */
            //doc = Jsoup.parse(html);


            //Elements question = doc.select("div#tabWindow");
            //System.out.println("size: " + question.size());

            //for(Element e : question)
            //    System.out.println("\n\n\n\n" + doc.html()+"\n\n\n\n\n");

            String pid = url.substring(url.indexOf("pid=")).replace("pid=", "");
            String toDBUrl = "http://bananarepublic.gap.com/browse/product.do?pid="+pid;
            float listPrice = new Float(extractBRInfo(html, "regularMaxPrice:$", ","));
            String name = extractBRInfo(html, "name:", ",");
            String descLength = extractBRInfo(html, "bulletAttributes:[", "]");
            String colorName = extractBRInfo(html, "productStyleColors:", "name");
            String colorCode = extractBRInfo(html, "productStyleColors:", "code");

            /*System.out.println(pid);
            System.out.println(toDBUrl);
            System.out.println(listPrice);
            System.out.println(name);
            System.out.println("description : " + descLength + "asdfadsfasefawefasdfasdf");
            System.out.println(colorName);
            System.out.println(colorCode);
            System.out.println(category);
            */

            //Check whether to update or insert
            PreparedStatement stmt = db.conn.prepareStatement("select COUNT(pid) as count from items where pid=? and site='br'");
            stmt.setString(1, pid);

            ResultSet rs = stmt.executeQuery();
            boolean update = true;
            while(rs.next()) {
                int count = rs.getInt("count");
                System.out.println("Count = " + count) ;
                if (count == 0)
                    update = false;

            }


            rs.close();
            stmt.close();

            //Add to database
            String sql = "";
            if(!update) {
                sql = insertSQL;
                insertInto(colorCode, colorName, listPrice, name, descLength, toDBUrl, pid,"br", category,sql);
            }
            else
            {
                sql = updateSQL;
                update(colorCode, colorName, listPrice, name, descLength, toDBUrl, pid,"br", category, sql);
            }

        }





    }

    private String extractBRInfo(String html, String info, String endString) throws StringIndexOutOfBoundsException{
        String toRet = "";

        int start = html.indexOf(info);
        int end = html.indexOf(endString, start);

        if(!info.equals("productStyleColors:"))
            toRet = html.substring(start, end).replace(info, "");
        else
        {
            if(endString.equals("code"))
            {
                int i = html.indexOf("swatchImage:");
                while(i != -1)
                {
                    String tmp = html.substring(i, html.indexOf(",", i)).replace("swatchImage:", "")+" ";
                    toRet+=tmp;
                    i = html.indexOf("swatchImage:", i+1);
                }
            }
            else
            {
                int i = html.indexOf("colorName:");
                while(i != -1)
                {
                    String tmp = html.substring(i, html.indexOf(",", i)).replace("colorName:", "")+" ";
                    toRet+=tmp;
                    i = html.indexOf("colorName:", i+1);
                }
            }
        }



        return toRet;
    }


}
