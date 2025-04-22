class Course {
    char name[20];
    char code[10][20];
    int credit;
    int courseWorkScore;

    void whereToAttend(){
        print_s((char*)"Not determined! The course will be held virtually or in person!\n");
    }
    int hasExam(){
        if(courseWorkScore == 100)
            return 0;
        else
            return 1;
    }
}

class VirtualCourse extends Course {
    char zoomLink[200];
    int isOnZoom;    
    
    void whereToAttend(){
        print_s((char*)"The course is going to be held on Zoom!\n");
    }
}


void main(){

    // name 
    char name[20];
    name[0] = 'a';
    name[1] = 'b';
    name[2] = 'c';
    name[3] = 'd';
    name[4] = 'e';
    name[5] = 'f';
    name[6] = 'g';
    name[7] = 'h';
    name[8] = '0';
    class Course comp520;
    comp520 = new class Course();
    int credit;
    credit = comp520.credit; 
    comp520.courseWorkScore = 100; 
    int i ;
    i = 0;
    while(i < 20){
        comp520.name[i] = name[i]; 
        i = i + 1;
        if ( name[i] == '0' ){
            break;
        }
    }
    comp520.whereToAttend();
    if(comp520.hasExam() == 0)
        print_s((char*)"No exam!\n");
    else
        print_s((char*)"Exam!\n");
    
    print_i((int)comp520.credit);
    print_c('\n');
    i = 0;
    while (i < 20){
        print_c(comp520.name[i]);
        if ( name[i] == '0' ){
            break;
        }
        i = i + 1;
        print_c('\n');
    }
    print_c('\n');
    print_i((int)comp520.courseWorkScore);
    print_c('\n');



    class Course vcourse;
    vcourse = (class Course) new class VirtualCourse();
    vcourse.whereToAttend();

    class Course course;
    course = (class Course) vcourse;
    course.credit = 4; //Valid

    print_c(comp520.name[19]);
    comp520.code[0][0] = 'C';
    print_c('\n');
    print_c(comp520.code[9][19]);
}