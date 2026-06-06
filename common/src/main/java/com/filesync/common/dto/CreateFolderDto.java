package com.filesync.common.dto;

import java.util.List;
import java.util.UUID;

public class CreateFolderDto {
    private String name;
    private List<MemberDto> members;  // optional initial members

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<MemberDto> getMembers() { return members; }
    public void setMembers(List<MemberDto> members) { this.members = members; }

}