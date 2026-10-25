package com.railreserve.train;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StationRepository extends JpaRepository<Station, Long> {
    @Query("""
        select s from Station s
        where s.active = true
          and (:query is null
               or lower(s.code) like lower(concat('%', :query, '%'))
               or lower(s.name) like lower(concat('%', :query, '%'))
               or lower(s.city) like lower(concat('%', :query, '%')))
        order by s.name
        """)
    List<Station> searchActive(@Param("query") String query);
}
