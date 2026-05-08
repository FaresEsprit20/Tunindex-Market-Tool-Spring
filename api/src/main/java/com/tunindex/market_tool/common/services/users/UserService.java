package com.tunindex.market_tool.common.services.users;

import com.tunindex.market_tool.common.dto.auth.ChangePasswordUserRequestDto;
import com.tunindex.market_tool.common.dto.user.ChangePasswordUserDto;
import com.tunindex.market_tool.common.dto.user.UserDto;
import com.tunindex.market_tool.common.dto.user.UserExtendedDto;
import com.tunindex.market_tool.common.dto.user.UserUpdateDto;
import com.tunindex.market_tool.common.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;


public interface UserService {

    UserDto save(UserDto userDto);

    boolean existsByEmail(String email);

    UserDto update(UserUpdateDto userDto, Authentication authentication);

    UserExtendedDto findById(Integer userId);

    Page<UserExtendedDto> findAll(Specification<User> specification, Pageable pageable);

    void delete(Integer userId);

    UserExtendedDto findByEmail(String email);

    UserExtendedDto changePassword(ChangePasswordUserRequestDto dto);

    UserExtendedDto changeProfilePassword(ChangePasswordUserDto dto);


    UserDto toggleLock(Integer userId);


    Integer findUserIdByEmail(String userEmail);
}