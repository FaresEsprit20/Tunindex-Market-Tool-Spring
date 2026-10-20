package com.tunindex.market_tool.api.specifications.users;

import com.tunindex.market_tool.api.entities.Roles;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.enums.UserRole;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

public class UserSpecification {

    public static Specification<User> withFilters(Map<String, String> filters) {
        return (root, query, cb) -> {
            System.out.println("=== UserSpecification.withFilters() called ===");
            System.out.println("Filters received: " + filters);
            
            if (filters == null || filters.isEmpty()) {
                System.out.println("No filters provided, returning all users");
                return cb.conjunction();
            }

            Predicate predicate = cb.conjunction();

            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                


                System.out.println("Processing filter - Key: " + key + ", Value: " + value);

                if (!StringUtils.hasText(value)) {
                    System.out.println("Skipping empty filter for key: " + key);
                    continue;
                }

                String searchTerm = "%" + value.trim().toLowerCase() + "%";

                switch (key) {
                    // Basic fields
                    case "firstName":
                        predicate = cb.and(predicate,
                                cb.like(cb.lower(root.get("firstName")), searchTerm));
                        break;
                    case "lastName":
                        predicate = cb.and(predicate,
                                cb.like(cb.lower(root.get("lastName")), searchTerm));
                        break;
                    case "email":
                        predicate = cb.and(predicate,
                                cb.like(cb.lower(root.get("email")), searchTerm));
                        break;

                    // Birthdate (exact match)
                    case "birthDate":
                        try {
                            Instant birthDate = Instant.parse(value);
                            predicate = cb.and(predicate,
                                    cb.equal(root.get("birthDate"), birthDate));
                        } catch (DateTimeParseException e) {
                            // Handle invalid date format
                        }
                        break;

                    // Address fields (unchanged from your version)
                    case "address1":
                        predicate = cb.and(predicate,
                                cb.like(cb.lower(root.get("address").get("address1")), searchTerm));
                        break;
                    case "address2":
                        predicate = cb.and(predicate,
                                cb.like(cb.lower(root.get("address").get("address2")), searchTerm));
                        break;
                    case "city":
                        predicate = cb.and(predicate,
                                cb.like(cb.lower(root.get("address").get("city")), searchTerm));
                        break;
                    case "zipCode":
                        predicate = cb.and(predicate,
                                cb.like(cb.lower(root.get("address").get("zipCode")), searchTerm));
                        break;
                    case "country":
                        predicate = cb.and(predicate,
                                cb.like(cb.lower(root.get("address").get("country")), searchTerm));
                        break;

                    // Role enum search - filter users who have the specified role
                    case "role":
                        try {
                            UserRole role = UserRole.valueOf(value.toUpperCase());
                            System.out.println("Filtering users by role: " + role);
                            
                            // Use INNER JOIN to find users who have the specified role
                            // This should only return users who actually have this role
                            Join<User, Roles> rolesJoin = root.join("roles", JoinType.INNER);
                            
                            // Filter by the specific role
                            predicate = cb.and(predicate, cb.equal(rolesJoin.get("roleName"), role));
                            
                            // Add DISTINCT to prevent duplicate results from multiple roles
                            query.distinct(true);
                            
                            System.out.println("Role filter applied for: " + role + " with DISTINCT");
                        } catch (IllegalArgumentException e) {
                            // Handle invalid role value - skip this filter
                            System.out.println("Invalid role value: " + value + ", error: " + e.getMessage());
                        }
                        break;

                    // Locked status
                    case "locked":
                        Boolean locked = Boolean.parseBoolean(value);
                        predicate = cb.and(predicate,
                                cb.equal(root.get("locked"), locked));
                        break;
                }
            }

            return predicate;
        };
    }


}