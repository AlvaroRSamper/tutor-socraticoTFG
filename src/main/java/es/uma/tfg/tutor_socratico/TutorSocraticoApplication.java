package es.uma.tfg.tutor_socratico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;



@SpringBootApplication
@EnableScheduling
public class TutorSocraticoApplication {

	public static void main(String[] args) {
		SpringApplication.run(TutorSocraticoApplication.class, args);
	}

}
