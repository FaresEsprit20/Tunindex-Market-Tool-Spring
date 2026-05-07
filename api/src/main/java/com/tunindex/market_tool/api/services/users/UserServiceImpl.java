package com.tunindex.market_tool.api.services.users;

import com.tunindex.market_tool.api.dto.auth.ChangePasswordUserRequestDto;
import com.tunindex.market_tool.api.dto.user.ChangePasswordUserDto;
import com.tunindex.market_tool.api.dto.user.UserDto;
import com.tunindex.market_tool.api.dto.user.UserExtendedDto;
import com.tunindex.market_tool.api.dto.user.UserUpdateDto;
import com.tunindex.market_tool.api.entities.Roles;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.embedded.Address;
import com.tunindex.market_tool.api.entities.enums.TokenType;
import com.tunindex.market_tool.api.entities.enums.UserRole;
import com.tunindex.market_tool.api.exception.EntityNotFoundException;
import com.tunindex.market_tool.api.exception.ErrorCodes;
import com.tunindex.market_tool.api.exception.InvalidEntityException;
import com.tunindex.market_tool.api.exception.InvalidOperationException;
import com.tunindex.market_tool.api.repository.RolesRepository;
import com.tunindex.market_tool.api.repository.UnifiedTokenRepository;
import com.tunindex.market_tool.api.repository.UserRepository;
import com.tunindex.market_tool.api.validators.email.EmailValidator;
import com.tunindex.market_tool.api.validators.password.PasswordValidator;
import com.tunindex.market_tool.api.validators.users.UserUpdateValidator;
import com.tunindex.market_tool.api.validators.users.UserValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RolesRepository rolesRepository;
    private final UnifiedTokenRepository unifiedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, RolesRepository rolesRepository, UnifiedTokenRepository unifiedTokenRepository) {
        this.userRepository = userRepository;
        this.rolesRepository = rolesRepository;
        this.unifiedTokenRepository = unifiedTokenRepository;
    }

    @Override
    @Transactional
    public UserDto save(UserDto dto) {
        List<String> errors = UserValidator.validate(dto);
        if (!errors.isEmpty()) {
            log.error("User is not valid {}", dto);
            log.warn("Errors : {}", errors);
            throw new InvalidEntityException("The user is not valid", ErrorCodes.USER_NOT_VALID, errors);
        }

        // Check if email already exists
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new InvalidEntityException("Another user with the same email already exists", ErrorCodes.USER_ALREADY_EXISTS,
                    Collections.singletonList("Another user with the same email already exists in the DB"));
        }

        // Validate phone number
        if (dto.getNumTel() == null || dto.getNumTel().trim().isEmpty()) {
            errors.add("Phone number is required");
            throw new InvalidEntityException("Phone number validation failed",
                    ErrorCodes.USER_NOT_VALID, errors);
        }

        String trimmedPhone = dto.getNumTel().trim();

        // Validate exactly 8 digits
        if (!trimmedPhone.matches("^\\d{8}$")) {
            errors.add("Phone number must be exactly 8 digits (0-9). No prefix like +216 allowed");
            throw new InvalidEntityException("Phone number validation failed",
                    ErrorCodes.USER_NOT_VALID, errors);
        }

        // Check if phone number already exists - THROW INVALID OPERATION EXCEPTION
        Optional<User> existingUserByPhone = userRepository.findUserByNumTel(trimmedPhone);
        if (existingUserByPhone.isPresent()) {
            throw new InvalidOperationException(
                    "Phone number " + trimmedPhone + " is already registered to another user",
                    ErrorCodes.USER_ALREADY_EXISTS,
                    Collections.singletonList("Phone number already exists")
            );
        }

        dto.setPassword(passwordEncoder.encode(dto.getPassword()));

        User userEntity = UserDto.toEntity(dto);

        // Handle roles - fetch existing roles from database
        if (userEntity.getRoles() != null && !userEntity.getRoles().isEmpty()) {
            List<Roles> managedRoles = new ArrayList<>();
            for (Roles role : userEntity.getRoles()) {
                Optional<Roles> existingRole = rolesRepository.findByRoleName(role.getRoleName());
                if (existingRole.isPresent()) {
                    managedRoles.add(existingRole.get());
                } else {
                    log.warn("Role {} not found in database, creating new one", role.getRoleName());
                    Roles newRole = new Roles();
                    newRole.setRoleName(role.getRoleName());
                    managedRoles.add(rolesRepository.save(newRole));
                }
            }
            userEntity.setRoles(managedRoles);
        }

        User savedUser = userRepository.save(userEntity);
        log.info("User saved successfully with ID: {} and roles: {}, phone number: {}",
                savedUser.getId(),
                savedUser.getRoles().stream().map(r -> r.getRoleName().name()).toList(),
                savedUser.getNumTel());

        return UserDto.fromEntity(savedUser);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public UserDto update(UserUpdateDto userDto, Authentication authentication) {
        List<String> errors = UserUpdateValidator.validate(userDto);
        if (!errors.isEmpty()) {
            log.error("User is not valid {}", userDto);
            log.warn("Errors : {}", errors);
            throw new InvalidEntityException("The user is not valid", ErrorCodes.USER_NOT_VALID, errors);
        }

        Optional<User> userEntity = userRepository.findUserByEmail(authentication.getName());
        if (userEntity.isEmpty()) {
            errors.add("User with Email = " + authentication.getName() + " is not found");
            throw new EntityNotFoundException("User with Email = " + authentication.getName() + " is not found",
                    ErrorCodes.USER_NOT_FOUND, errors);
        }
        if (!userDto.getEmail().equals(userEntity.get().getEmail())) {
            log.warn("Mismatch between authenticated user and provided email");
            errors.add("Authenticated user does not match the email in request body");
            throw new InvalidOperationException("Impossible to proceed with update",
                    ErrorCodes.USER_NOT_VALID, errors);
        }

        // Validate phone number if it's being changed
        if (userDto.getNumTel() != null && !userDto.getNumTel().equals(userEntity.get().getNumTel())) {
            String trimmedPhone = userDto.getNumTel().trim();

            // Validate exactly 8 digits
            if (!trimmedPhone.matches("^\\d{8}$")) {
                errors.add("Phone number must be exactly 8 digits (0-9). No prefix like +216 allowed");
                throw new InvalidEntityException("Phone number validation failed",
                        ErrorCodes.USER_NOT_VALID, errors);
            }

            // Check if phone number already exists for another user
            Optional<User> existingUserByPhone = userRepository.findUserByNumTel(trimmedPhone);
            if (existingUserByPhone.isPresent() && !existingUserByPhone.get().getId().equals(userEntity.get().getId())) {
                throw new InvalidOperationException(
                        "Phone number " + trimmedPhone + " is already registered to another user",
                        ErrorCodes.USER_ALREADY_EXISTS,
                        Collections.singletonList("Phone number already exists")
                );
            }

            userEntity.get().setNumTel(trimmedPhone);
        }

        Address address = userEntity.get().getAddress();
        address.setAddress1(userDto.getAddress().getAddress1());
        address.setAddress2(userDto.getAddress().getAddress2());
        address.setCity(userDto.getAddress().getCity());
        address.setCountry(userDto.getAddress().getCountry());
        address.setZipCode(userDto.getAddress().getZipCode());
        userEntity.get().setAddress(address);
        userEntity.get().setPhoto(userDto.getPhoto());

        return UserDto.fromEntity(userRepository.save(userEntity.get()));
    }

    @Transactional
    protected boolean userAlreadyExists(String email) {
        Optional<User> user = userRepository.findUserByEmail(email);
        return user.isPresent();
    }

    @Override
    @Transactional
    public UserExtendedDto findById(Integer userId) {
        List<String> errors = new ArrayList<>();
        if (userId == null) {
            log.error("User ID is null");
            errors.add("User ID is null");
            throw new InvalidEntityException("The user ID is not valid", ErrorCodes.USER_NOT_VALID, errors);
        }
        errors.add("No user with the ID = " + userId + " has been found in the DB");
        return userRepository.findById(userId)
                .map(UserExtendedDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No user with the ID = " + userId + " has been found in the DB",
                        ErrorCodes.USER_NOT_FOUND, errors)
                );
    }

    @Override
    @Transactional
    public Page<UserExtendedDto> findAll(Specification<User> specification, Pageable pageable) {
        return userRepository.findAll(specification, pageable)
                .map(UserExtendedDto::fromEntity);
    }

    @Override
    @Transactional
    public UserExtendedDto findByEmail(String email) {
        List<String> errors = EmailValidator.validate(email);
        if (!errors.isEmpty()) {
            log.error("User Email is Not Valid");
            errors.add("User Email is Not Valid");
            throw new InvalidEntityException("User Email is Not Valid",
                    ErrorCodes.USER_NOT_VALID, errors);
        }
        errors.add("No user with the email = " + email + " has been found in the DB");
        return userRepository.findUserByEmail(email)
                .map(UserExtendedDto::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No user with the email = " + email + " has been found in the DB",
                        ErrorCodes.USER_NOT_FOUND, errors)
                );
    }

    @Override
    @Transactional
    public UserExtendedDto changePassword(ChangePasswordUserRequestDto dto) {
        validatePassword(dto);
        List<String> errors = new ArrayList<>();
        Optional<User> utilisateurOptional = userRepository.findById(dto.getId());
        if (utilisateurOptional.isEmpty()) {
            errors.add("No user with the ID = " + dto.getId() + " has been found in the DB");
            log.warn("No User has been found with the ID " + dto.getId());
            throw new EntityNotFoundException("No User has been found with the ID " + dto.getId(),
                    ErrorCodes.USER_NOT_FOUND, errors);
        }

        User user = utilisateurOptional.get();
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        return UserExtendedDto.fromEntity(userRepository.save(user));
    }

    @Override
    public UserExtendedDto changeProfilePassword(ChangePasswordUserDto dto) {
        validatePassword(dto);
        List<String> errors = new ArrayList<>();
        Optional<User> utilisateurOptional = userRepository.findById(dto.getId());
        if (utilisateurOptional.isEmpty()) {
            errors.add("No user with the ID = " + dto.getId() + " has been found in the DB");
            log.warn("No User has been found with the ID " + dto.getId());
            throw new EntityNotFoundException("No User has been found with the ID " + dto.getId(),
                    ErrorCodes.USER_NOT_FOUND, errors);
        }

        User user = utilisateurOptional.get();
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        return UserExtendedDto.fromEntity(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDto toggleLock(Integer userId) {
        List<String> errors = new ArrayList<>();
        if (userId == null) {
            errors.add("User ID is null");
            log.error("User ID is null");
            throw new InvalidEntityException("The user ID is not valid", ErrorCodes.USER_NOT_VALID, errors);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No user with the ID = " + userId + " has been found in the DB",
                        ErrorCodes.USER_NOT_FOUND, List.of("No user with the ID = " + userId + " has been found in the DB"))
                );

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getRoleName().name().equals("ADMIN"));

        if (isAdmin) {
            errors.add("Impossible to lock/unlock user with ID " + userId +
                    " who has role ADMIN — INVALID OPERATION");
            throw new InvalidOperationException(
                    "Impossible to lock/unlock user with ID " + userId +
                            " who has role ADMIN — INVALID OPERATION",
                    ErrorCodes.USER_ACCOUNT_LOCK_NOT_VALID, errors);
        }

        user.setLocked(!user.getLocked());
        return UserDto.fromEntity(userRepository.save(user));
    }

    @Override
    public Integer findUserIdByEmail(String userEmail) {
        List<String> errors = new ArrayList<>();
        errors.add("No user with the email = " + userEmail + " has been found in the DB");
        return userRepository.findUserIdByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User with Email " + userEmail + " has not been found",
                        ErrorCodes.USER_NOT_FOUND,
                        errors
                ));
    }

    @Transactional
    protected void validatePassword(ChangePasswordUserRequestDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto == null) {
            log.warn("Impossible to modify the password with a null object");
            errors.add("Impossible to modify the password with a null object");
            throw new InvalidOperationException("No Information has been provided to proceed for changing the password",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }
        if (dto.getId() == null) {
            log.warn("Impossible to modify the password with a NULL ID");
            errors.add("Impossible to modify the password with a NULL ID");
            throw new InvalidOperationException("ID user is null:: Impossible to modify the password ",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }
        if (!StringUtils.hasLength(dto.getPassword()) || !StringUtils.hasLength(dto.getConfirmPassword())) {
            log.warn("Impossible to modify the password with a NULL password");
            errors.add("Impossible to modify the password with a NULL password");
            throw new InvalidOperationException("Null Password:: Impossible to modify the password",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            log.warn("Impossible to modify the password when your password and confirm password are not the same ");
            errors.add("Impossible to modify the password: Passwords do not match");
            throw new InvalidOperationException("User Passwords mismatch:: Impossible to modify the password when your password and confirm password are not the same",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }

        List<String> passwordErrors = PasswordValidator.validate(dto.getPassword());
        if (!passwordErrors.isEmpty()) {
            errors.addAll(passwordErrors);
            throw new InvalidOperationException("Password Validation Error: ",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }
    }

    @Transactional
    protected void validatePassword(ChangePasswordUserDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto == null) {
            log.warn("Impossible to modify the password with a null object");
            errors.add("Impossible to modify the password with a null object");
            throw new InvalidOperationException("No Information has been provided to proceed for changing the password",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }
        if (dto.getId() == null) {
            log.warn("Impossible to modify the password with a NULL ID");
            errors.add("Impossible to modify the password with a NULL ID");
            throw new InvalidOperationException("ID user is null:: Impossible to modify the password ",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }
        if (!StringUtils.hasLength(dto.getPassword()) || !StringUtils.hasLength(dto.getConfirmPassword())) {
            log.warn("Impossible to modify the password with a NULL password");
            errors.add("Impossible to modify the password with a NULL password");
            throw new InvalidOperationException("Null Password:: Impossible to modify the password",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            log.warn("Impossible to modify the password when your password and confirm password are not the same ");
            errors.add("Impossible to modify the password: Passwords do not match");
            throw new InvalidOperationException("User Passwords mismatch:: Impossible to modify the password when your password and confirm password are not the same",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }

        List<String> passwordErrors = PasswordValidator.validate(dto.getPassword());
        if (!passwordErrors.isEmpty()) {
            errors.addAll(passwordErrors);
            throw new InvalidOperationException("Password Validation Error: ",
                    ErrorCodes.USER_CHANGE_PASSWORD_OBJECT_NOT_VALID, errors);
        }
    }

    @Override
    @Transactional
    public void delete(Integer userId) {
        List<String> errors = new ArrayList<>();

        if (userId == null) {
            log.error("User ID is null");
            errors.add("User ID is null");
            throw new InvalidEntityException(
                    "The user ID is not valid",
                    ErrorCodes.USER_NOT_VALID,
                    errors
            );
        }

        errors.add("No user with the ID = " + userId + " has been found in the DB");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No user with the ID = " + userId + " has been found in the DB",
                        ErrorCodes.USER_NOT_FOUND,
                        errors
                ));

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getRoleName().name().equals("ADMIN"));

        if (isAdmin) {
            String errorMessage = "Impossible to delete a user with ID " + userId +
                    " who has role ADMIN — INVALID OPERATION";
            log.error(errorMessage);
            errors.add(errorMessage);
            throw new InvalidOperationException(
                    errorMessage,
                    ErrorCodes.USER_NOT_VALID,
                    errors
            );
        }

        // Delete all JWT tokens for the user
        unifiedTokenRepository.deleteByUserEmailAndType(user.getEmail(), TokenType.JWT);
        rolesRepository.deleteAllRolesForUser(userId);
        userRepository.deleteByIdCustom(userId);
    }
}