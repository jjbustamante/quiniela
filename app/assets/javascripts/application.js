// This is a manifest file that'll be compiled into application.js, which will include all the files
// listed below.
//
// Any JavaScript/Coffee file within this directory, lib/assets/javascripts, vendor/assets/javascripts,
// or vendor/assets/javascripts of plugins, if any, can be referenced here using a relative path.
//
// It's not advisable to add code directly here, but if you do, it'll appear at the bottom of the
// the compiled file.
//
// WARNING: THE FIRST BLANK LINE MARKS THE END OF WHAT'S TO BE PROCESSED, ANY BLANK LINE SHOULD
// GO AFTER THE REQUIRES BELOW.
//
//= require jquery
//= require jquery_ujs
//= require_tree .
//= require_tree ../../../vendor/assets/javascripts

var customData = {};
var DRAW_VALUE = 1000000;
openMenu = function(e){
	$(".dropdown").addClass("open");
};
 
/* Edit function is called when team label is clicked */
function edit_fn(container, data, doneCb) {
  //alert("editado");
}
 
/* Render function is called for each team label when data is changed, data
 * contains the data object given in init and belonging to this slot. */
function render_fn(container, data, score) {
  if (!data.flag || !data.name)
    return

	
  container.append('<span team-name="'+data.name+'" class="flagsp flagsp_'+
  	data.flag+'" title=""> </span>').append(data.name)
 

}
initializeBrackets = function(customData){
	  $('div#customHandlers .demo').bracket({
	    init: customData,    
	    save: matchEdited, /* without save() labels are disabled */
	    decorator: {edit: edit_fn,
	                render: render_fn}});
	  $(".jQBracket").css("width","762px");
	  checkDraws();
};
function getMatchesChildrenId(matchIndex){

	if(matchIndex==0 || matchIndex==1)
		return 8;
	
	if(matchIndex==2 || matchIndex==3)
		return 9;
	
	if(matchIndex==4 || matchIndex==5)
		return 10;
	
	if(matchIndex==6 || matchIndex==7)
		return 11;
	
	if(matchIndex==8 || matchIndex==9)
		return 12;
	
	if(matchIndex==10 || matchIndex==11)
		return 13;
	
	if(matchIndex==12 || matchIndex==13)
		return 14;
	
}
 function matchEdited(data, userData) {
 	
 	var sTeams = [0,1];
 	var fTeams = [0,1];
 	var cTeams = [];
 	var matchCheckpoint = 0;

 	var allMatches = [[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}],[{},{}]];
 	var qMatches = [[{},{}],[{},{}],[{},{}],[{},{}]];
 	var sMatches = [[{},{}],[{},{}]];
 	var fMatches = [[{},{}],[{},{}]];
 	var cMatches = [[{},{}],[{},{}]];
 	
 	$.each(data.results[0],function(roundIndex,matches){
 		//ronda de octavos
 		
 		if(roundIndex==0){
 			var matchPosition = 0;
 			$.each(matches,function(matchIndex,match){
		 		var team1Match = customData.teams[matchIndex][0];
		 		var team2Match = customData.teams[matchIndex][1];
		 		var matchId = team1Match.matchId;

		 		var scoreT1 = customData.results[0][roundIndex][matchIndex][0];
		 		var scoreT2 = customData.results[0][roundIndex][matchIndex][1];
		 		var deuce = false;
		 		

		 		var nextMatchPosition = getMatchesChildrenId(matchIndex) - 8;

		 		var matchItemT1 = {};
		 			matchItemT1.teamId = team1Match.teamId;
		 			matchItemT1.team1Score = scoreT1;

				var matchItemT2 = {};
					matchItemT2.teamId = team2Match.teamId;
					matchItemT2.team2Score = scoreT2;
					
					allMatches[matchCheckpoint] = [matchItemT1,matchItemT2];

					matchCheckpoint++;

		 		if(scoreT1 > scoreT2){
		 			//qTeams.push(team1Match);
		 			var matchItem = {};
			 			matchItem.teamId = team1Match.teamId;
			 			matchItem.teamScore = 0;
			 		var arrayItem = qMatches[nextMatchPosition];
			 			arrayItem[matchPosition]	= matchItem;
						qMatches[nextMatchPosition] = arrayItem;
		 		}else if(scoreT1 < scoreT2){
		 			//qTeams.push(team2Match);
					var matchItem = {};
			 			matchItem.teamId = team2Match.teamId;
			 			matchItem.teamScore = 0;
					var arrayItem = qMatches[nextMatchPosition];
			 			arrayItem[matchPosition]	= matchItem;
						qMatches[nextMatchPosition] = arrayItem;

		 		}else{
		 			deuce = true;
		 		}

		 		matchPosition++;
		 		if(matchPosition > 1)
		 			matchPosition = 0;
		 	});
 		}else if(roundIndex==1){
 			var matchPosition = 0;
 			$.each(matches,function(matchIndex,match){
 				var team1Match = qMatches[matchIndex][0];
		 		var team2Match = qMatches[matchIndex][1];
 				var scoreT1 = match[0];
 				var scoreT2 = match[1];
 				
 				var nextMatchPosition = getMatchesChildrenId(matchIndex + 8) - 12;

 				var matchItemT1 = {};
		 			matchItemT1.teamId = team1Match.teamId;
		 			matchItemT1.team1Score = scoreT1;

				var matchItemT2 = {};
					matchItemT2.teamId = team2Match.teamId;
					matchItemT2.team2Score = scoreT2;

				allMatches[matchCheckpoint] = [matchItemT1,matchItemT2];

				matchCheckpoint++;

				if(scoreT1 > scoreT2){
		 			//qTeams.push(team1Match);
		 			var matchItem = {};
			 			matchItem.teamId = team1Match.teamId;
			 			matchItem.teamScore = 0;
			 		var arrayItem = sMatches[nextMatchPosition];
			 			arrayItem[matchPosition]	= matchItem;
						sMatches[nextMatchPosition] = arrayItem;
		 		}else if(scoreT1 < scoreT2){
		 			//qTeams.push(team2Match);
					var matchItem = {};
			 			matchItem.teamId = team2Match.teamId;
			 			matchItem.teamScore = 0;
					var arrayItem = sMatches[nextMatchPosition];
			 			arrayItem[matchPosition]	= matchItem;
						sMatches[nextMatchPosition] = arrayItem;

		 		}else{
		 			deuce = true;
		 		}

		 		matchPosition++;
		 		if(matchPosition > 1)
		 			matchPosition = 0;

 			});
 		}else if(roundIndex==2){
 			var matchPosition = 0;
 			$.each(matches,function(matchIndex,match){
 				var team1Match = sMatches[matchIndex][0];
		 		var team2Match = sMatches[matchIndex][1];
 				var scoreT1 = match[0];
 				var scoreT2 = match[1];
 				
 				var nextMatchPosition = getMatchesChildrenId(matchIndex + 12) - 14;

 				var matchItemT1 = {};
		 			matchItemT1.teamId = team1Match.teamId;
		 			matchItemT1.team1Score = scoreT1;

				var matchItemT2 = {};
					matchItemT2.teamId = team2Match.teamId;
					matchItemT2.team2Score = scoreT2;

				allMatches[matchCheckpoint] = [matchItemT1,matchItemT2];

				matchCheckpoint++;

				if(scoreT1 > scoreT2){
		 			//qTeams.push(team1Match);
		 			var matchItem = {};
			 			matchItem.teamId = team1Match.teamId;
			 			matchItem.teamScore = 0;
			 		var arrayItem = fMatches[nextMatchPosition];
			 			arrayItem[matchPosition]	= matchItem;
						fMatches[nextMatchPosition] = arrayItem;

					
					var matchItem = {};
			 			matchItem.teamId = team2Match.teamId;
			 			matchItem.teamScore = 0;
			 		var arrayItem = cMatches[nextMatchPosition];
			 			arrayItem[matchPosition]	= matchItem;
						cMatches[nextMatchPosition] = arrayItem;

		 		}else if(scoreT1 < scoreT2){
		 			//qTeams.push(team2Match);
					var matchItem = {};
			 			matchItem.teamId = team2Match.teamId;
			 			matchItem.teamScore = 0;
					var arrayItem = fMatches[nextMatchPosition];
			 			arrayItem[matchPosition]	= matchItem;
						fMatches[nextMatchPosition] = arrayItem;

					var matchItem = {};
			 			matchItem.teamId = team1Match.teamId;
			 			matchItem.teamScore = 0;
					var arrayItem = cMatches[nextMatchPosition];
			 			arrayItem[matchPosition]	= matchItem;
						cMatches[nextMatchPosition] = arrayItem;

		 		}else{
		 			deuce = true;
		 		}

		 		matchPosition++;
		 		if(matchPosition > 1)
		 			matchPosition = 0;

 			});
 		}else if(roundIndex==3){
 			//var matchPosition = 0;
 			$.each(matches,function(matchIndex,match){
 				if(matchIndex == 0){
 					var team1Match = fMatches[0][0];
		 			var team2Match = fMatches[0][1];
 				}else{
 					var team1Match = cMatches[0][0];
		 			var team2Match = cMatches[0][1];
 				}
 				var scoreT1 = match[0];
 				var scoreT2 = match[1];
 				
 				var matchItemT1 = {};
		 			matchItemT1.teamId = team1Match.teamId;
		 			matchItemT1.team1Score = scoreT1;

				var matchItemT2 = {};
					matchItemT2.teamId = team2Match.teamId;
					matchItemT2.team2Score = scoreT2;

				allMatches[matchCheckpoint] = [matchItemT1,matchItemT2];

				matchCheckpoint++;

 			});

 		}

 	});


	//ajustamos el orden de final y 3er ligar

	//se actualizan los inputs
	var aux1 = allMatches[14];
		allMatches[14] = allMatches[15];
		allMatches[15] = aux1;


	var matchId = 49;
	$.each(allMatches,function(matchIndex,match){
		updateMatchRow(matchId,match[0].teamId,match[1].teamId,match[0].team1Score,match[1].team2Score);
		matchId++;
	});

	//se verifica si hay empates
	checkDraws();
 }
updateMatchRow = function(matchId,team1Id,team2Id,team1Score,team2Score){
	//actualizamos el id del primer team
	$("#quiniela_bets_attributes_"+(matchId-1)+"_team1_id").val(team1Id);
	$("#quiniela_bets_attributes_"+(matchId-1)+"_team2_id").val(team2Id);

	if(team1Id==null)
		team1Score = "";
	if(team2Id==null)
		team2Score = "";
	
	if(team1Score>team2Score){
		$("#quiniela_bets_attributes_"+(matchId-1)+"_winner_id").val(team1Id);
	}else{
		$("#quiniela_bets_attributes_"+(matchId-1)+"_winner_id").val(team2Id);
	}
	
	if(team1Score==DRAW_VALUE){
		team1Score = team2Score;
	}
	if(team2Score==DRAW_VALUE){
		team2Score = team1Score;
	}

	//Actualizamos el value de cada score
	$("#quiniela_bets_attributes_"+(matchId-1)+"_score_t1").val(team1Score);
	$("#quiniela_bets_attributes_"+(matchId-1)+"_score_t2").val(team2Score);
	
};
checkDraws = function(){
	var allFill = true;
	$(".score.editable").each(function(index,e){
		if(index % 2 == 0){
			var val1 = $("[data-resultid=result-"+index+"]");
			var val2 = $("[data-resultid=result-"+(index+1)+"]");
			var addRadios = true;
			if(val1.html()==DRAW_VALUE){
				val1.html(val2.html());
				addRadios = false;
			}
			if(val2.html()==DRAW_VALUE){
				val2.html(val1.html());
				addRadios = false;
			}
			if((val1.html()!="--" && val2.html()!="--") && val1.html()==val2.html()){
				if(addRadios){
					val1.prev(".label.editable").prepend('<input data-container="body" data-toggle="popover" data-content="Debes elegir el ganador en caso de empates" class="radio-pop"  style="float: left;width: 15px;margin-top: 2px;" type="radio" onclick="updateScore(this,'+(index)+')" name="asd">');
					val2.prev(".label.editable").prepend('<input style="float: left;width: 15px;margin-top: 2px;" type="radio" onclick="updateScore(this,'+(index+1)+')" name="asd">');
					$('.radio-pop').popover({
					    trigger: 'focus',
					        'placement': 'left'
					}).focus();
				}
			}else if(val1.html()=="--" || val2.html()=="--"){
				//alert("Por favor completa todos los resultados.");
			}
				
		}
	});
};





updateScore = function(e,teamId){
	var val1 = $("[data-resultid=result-"+teamId+"]");
	var scoreElement = $(e).parent(".label.editable").next(".score.editable");
	var scoreParent = scoreElement.parent();
	var aux = scoreElement.html();
	scoreElement.click();
	scoreElement.children("input[type=text]").val(DRAW_VALUE).blur();
}

showNotification = function(message,type){
	if(!($(".bootstrap-growl").length > 0))
	$.bootstrapGrowl(message, {
                        type: type,
                        align: 'center',
                        stackup_spacing: 30,
                        width: "100%"
                    });
};

function readURL(input) {

    if (input.files && input.files[0]) {
        var reader = new FileReader();

        reader.onload = function (e) {
            $('#user_preview').attr('src', e.target.result);
        }

        reader.readAsDataURL(input.files[0]);
    }
}
setWinnerId = function(e,teamId,matchId){
	$("#"+matchId).children("tbody").children("tr").children("td").children("#match_winner_id").val(teamId);
};
checkForDraw = function(e){
	var matchId = $(e).attr("match_id");
	var valt1 = $("#"+matchId).children("tbody").children("tr").children("td").children("#match_score_t1").val();
	var valt2 = $("#"+matchId).children("tbody").children("tr").children("td").children("#match_score_t2").val();
	if(valt1==valt2){
		$("#"+matchId).children("tbody").children("tr").children("td").children(".hidden-radio").show();
	}else{
		$("#"+matchId).children("tbody").children("tr").children("td").children(".hidden-radio").hide();
	}


};
