package com.example.pop.siresp;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SirespConfigCampoRepository extends JpaRepository<SirespConfigCampo, Long> {

    Optional<SirespConfigCampo> findByCampo(String campo);
}
