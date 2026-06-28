package com.reForm.backend.user.entity;

import com.reForm.backend.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Entity
@Table(name="workspaces")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workspace extends BaseEntity {

    @Column(nullable = false)
    private String name;


    private String description; 

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, name = "owner_id")
    private User owner;
    //select * from workspace where ws id is true

    //WORkspace workspace_id owner_id fk
    //User uuid workspace_id fk


//    this is the many to many, but now we want: one user only have 1 workspace, and can be invited to many workspaces
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "workspace_members",
            joinColumns = @JoinColumn(name = "workspace_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id"), // <-- Added the missing comma here
            indexes = {
                    @Index(name = "idx_workspace_member", columnList = "workspace_id, member_id")
            }
    )
    @Builder.Default
    private Set<User> members = new HashSet<>();
    //if you dont have builder.default, when you make new workspace, the builder point to null


//    @OneToMany(fetch = FetchType.LAZY)
//    private Set<User> members = new HashSet<>();
}
