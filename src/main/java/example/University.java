package example;

class University {
    private int numberOfDepartments;
    int numberOfLecturers;
    String name;
    String location;
    String address;

    public University() {

    }
    
    public University(int numberOfDepartments, int numberOfLecturers, String name, String location, String address) {
        this.numberOfDepartments = numberOfDepartments;
        this.numberOfLecturers = numberOfLecturers;
        this.name = name;
        this.location = location;
        this.address = address;
    }

    public University( String name, String location, String address) {
           this.numberOfDepartments = 0;
        this.numberOfLecturers = 0;
        this.name = name;
        this.location = location;
        this.address = address;
    }
}
