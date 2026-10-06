package example;

import java.util.ArrayList;
import java.util.List;

public class Main {

  

  public static void main(String args[]) {

    University uni1 = new University();

    University uni2 = new University();

    University uni3 = new University();

    System.out.println(uni1.address);

    University uba = new University(23, 90, "University of Bamenda", "Bamenda", "Bambili");

    University buea = new University(23, 90, "University of Buea", "Buea", "Moliko");

    System.out.println(uba.name);

    System.out.println(buea.name);



    University sl = new University("University of St Louis", "Bamenda", "Mile 3");


  }
}

//Local primitive variables live in stack memory. 
// If a primitive is part of an object or class, it’s stored in the heap right alongside the object. 
// There’s no wrapper or extra memory overhead either way, so access stays quick and direct.


//stack is per thread
//1 cpu = 1 thread( simplistic view,old design)
//hardware threads represent the number of concurrent execution context the CPU can handle
//many software threads