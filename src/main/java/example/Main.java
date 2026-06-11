package example;

import java.util.ArrayList;
import java.util.List;

public class Main {

  int age = 10;
  String name = "my name";
  double price = 4.13;
  float pi = 3.17f;

  long distance = 90;
  byte mybyte = 120;
  List<Byte> myBytes = new ArrayList<Byte>();

  char grade = 'B';

  public static void main(String args[]) {

    //statistically typed
    ///dynamically typed

  }
}

//Local primitive variables live in stack memory. 
// If a primitive is part of an object or class, it’s stored in the heap right alongside the object. 
// There’s no wrapper or extra memory overhead either way, so access stays quick and direct.


//stack is per thread
//1 cpu = 1 thread( simplistic view,old design)
//hardware threads represent the number of concurrent execution context the CPU can handle
//many software threads