package com.example;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PersonService {
    private final PersonRepository repository;

    public PersonService(PersonRepository repository) {
        this.repository = repository;
    }

    public List<Person> getAllPersons() {
        return repository.findAll();
    }

    public Person createPerson(Person person) {
        return repository.save(person);
    }

    public Person updatePerson(Long id, Person updatedPerson) {
        return repository.findById(id).map(person -> {
            person.setName(updatedPerson.getName());
            person.setAge(updatedPerson.getAge());
            person.setProfession(updatedPerson.getProfession());
            return repository.save(person);
        }).orElseThrow(() -> new RuntimeException("Person not found"));
    }

    public void deletePerson(Long id) {
        repository.deleteById(id);
    }
}
