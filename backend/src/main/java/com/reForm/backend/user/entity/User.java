package com.reForm.backend.user.entity;


import com.reForm.backend.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_user_email", columnList = "email")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_user_email", columnNames = {"email"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "username", nullable = false)
    private String username;

    //check this
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

//    //can be many to one, referential integrity, need to find new owner when delete that user
//    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "members")
//    private Set<Workspace> workspaces = new HashSet<>();
//    //mapped by members, to mark that this is the non-owing side (jointable is in the Workspace class,
//    //also, there must be variable called members in the Workspace class, do not mismatch them
//    //cascade all here is dangerous, once user is deleted, all workspaces belonging to them will be deleted too
//    //user_id work_spaceid
    // Fixed: Standard ManyToMany mapping (mappedBy points to 'members' in Workspace)
    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "members")
    @Builder.Default // Fixed: Prevents Lombok Builder from setting this to null!
    private Set<Workspace> workspaces = new HashSet<>();
}

