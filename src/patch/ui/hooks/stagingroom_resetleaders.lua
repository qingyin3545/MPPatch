if _mpPatch and _mpPatch.loaded and _mpPatch.isModding then
    local requestPlayerInfo = _mpPatch.registerChatCommand("requestPlayerInfo", function()
        if Matchmaking.IsHost() then
            Network.BroadcastPlayerInfo()
        end
    end)

    Events.SystemUpdateUI.Add(function (uiType, screen)
        if not ContextPtr:IsHidden() and not Matchmaking.IsHost() and
           uiType == SystemUpdateUIType.RestoreUI and screen == "StagingRoom" then
            _mpPatch.debugPrint("Requesting user info update.")
            requestPlayerInfo()
        end
    end)
end
