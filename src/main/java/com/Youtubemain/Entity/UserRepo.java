package com.Youtubemain.Entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserRepo extends JpaRepository<youtubeEntity, Long> {
    List<youtubeEntity> findByChannelName(String channelName);
}
