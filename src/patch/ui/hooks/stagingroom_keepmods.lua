if _mpPatch and _mpPatch.loaded and _mpPatch.isModding then
    _mpPatch.setBIsModding()

    local function cancelOverride()
        if context == ContextPtr and not Matchmaking.IsHost() then
            _mpPatch.debugPrint("Cancelling override.")
            _mpPatch.patch.NetPatch.reset()
        end
    end

    -- Hook simple modding functions.
    Modding = _mpPatch.hookTable(Modding, {ActivateAllowedDLC = function(...)
        _mpPatch.overrideModsFromPreGame()
        return Modding._super.ActivateAllowedDLC(...)
    end})

    Matchmaking = _mpPatch.hookTable(Matchmaking, {LaunchMultiplayerGame = function(...)
        _mpPatch.overrideModsFromPreGame()
        return Matchmaking._super.LaunchMultiplayerGame(...)
    end})

    -- Protocol for ensuring non-host players will override the mod list.
    local gameLaunchSet = false
    local gameLaunchCountdown = 3

    local doClientOverride = _mpPatch.registerChatCommand("doClientOverride", function(_, id)
        if id == m_HostID and not Matchmaking.IsHost() then
            gameLaunchSet = true
            _mpPatch.overrideModsFromPreGame()
        end
    end)

    local StartCountdownOld = StartCountdown
    function StartCountdown(...)
        if gameLaunchSet then return end
        return StartCountdownOld(...)
    end

    local StopCountdownOld = StopCountdown
    function StopCountdown(...)
        if gameLaunchSet then return end
        return StopCountdownOld(...)
    end

    local HandleExitRequestOld = HandleExitRequest
    function HandleExitRequest(...)
        if gameLaunchSet then return end
        cancelOverride()
        return HandleExitRequestOld(...)
    end

    local LaunchGameOld = LaunchGame
    local function LaunchGameCountdown(timeDiff)
        gameLaunchCountdown = gameLaunchCountdown - timeDiff
        if gameLaunchCountdown <= 0 then
            LaunchGameOld()
            ContextPtr:ClearUpdate()
        end
    end
    function LaunchGame(...)
        if PreGame.IsHotSeatGame() then
            return LaunchGameOld(...)
        else
            doClientOverride()
            ContextPtr:ClearUpdate()
            gameLaunchSet = true
            ContextPtr:SetUpdate(LaunchGameCountdown)
        end
    end
    Controls.LaunchButton:RegisterCallback(Mouse.eLClick, LaunchGame)

    -- Ensure the NetPatch hook doesn't end up escaping the UI.
    local DequeuePopup = UIManager.DequeuePopup
    _mpPatch.patch.globals.rawset(UIManager, "DequeuePopup", function(this, ...)
        local context = ...
        cancelOverride()
        return DequeuePopup(this, ...)
    end)
end

