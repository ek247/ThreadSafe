package Statistics;

/**
 * Created by worri on 6/3/2016.
 */
public class ProductData {
    String name;
    float price;
    String desc;
    String URL;
    String color;
    int pid;
    double weight;

    public ProductData()
    {
        pid = -1;
        name = "";
        price = 0;
        desc = "";
        URL = "";
        color = "";
        weight = 0;
    }

    //Auto generated
    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }
    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public String getName() {
        return name;
    }

    public float getPrice() {
        return price;
    }

    public String getDesc() {
        return desc;
    }

    public String getURL() {
        return URL;
    }

    public String getColor() {
        return color;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(float price) {
        this.price = price;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public void setURL(String URL) {
        this.URL = URL;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
