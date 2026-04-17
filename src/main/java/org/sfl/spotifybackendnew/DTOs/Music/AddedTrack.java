package org.sfl.spotifybackendnew.DTOs.Music;

import org.sfl.spotifybackendnew.DTOs.User.UserProfile;

public record AddedTrack(Track track, UserProfile profile) { }